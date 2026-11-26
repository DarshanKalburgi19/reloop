package com.reloop.orderpayment.service;

import com.reloop.orderpayment.client.PaymentConfirmation;
import com.reloop.orderpayment.client.RepairPaymentEvent;
import com.reloop.orderpayment.client.ProductServiceClient;
import com.reloop.orderpayment.client.RepairServiceClient;
import com.reloop.orderpayment.dto.payment.SimulatePaymentRequest;
import com.reloop.orderpayment.dto.payment.SimulatedOutcome;
import com.reloop.orderpayment.dto.payment.TransactionView;
import com.reloop.orderpayment.entity.CommissionRecord;
import com.reloop.orderpayment.entity.PaymentTransaction;
import com.reloop.orderpayment.entity.ProviderPayout;
import com.reloop.orderpayment.entity.PurchaseOrder;
import com.reloop.orderpayment.entity.RepairQuote;
import com.reloop.orderpayment.enums.CommissionStatus;
import com.reloop.orderpayment.enums.OrderStatus;
import com.reloop.orderpayment.enums.PaymentStatus;
import com.reloop.orderpayment.enums.PayoutStatus;
import com.reloop.orderpayment.enums.ProviderType;
import com.reloop.orderpayment.enums.QuoteStatus;
import com.reloop.orderpayment.enums.TransactionType;
import com.reloop.orderpayment.exception.BadRequestException;
import com.reloop.orderpayment.exception.ConflictException;
import com.reloop.orderpayment.exception.NotFoundException;
import com.reloop.orderpayment.exception.UnprocessableException;
import com.reloop.orderpayment.repository.CommissionRecordRepository;
import com.reloop.orderpayment.repository.PaymentTransactionRepository;
import com.reloop.orderpayment.repository.ProviderPayoutRepository;
import com.reloop.orderpayment.repository.PurchaseOrderRepository;
import com.reloop.orderpayment.repository.RepairQuoteRepository;
import com.reloop.orderpayment.security.AuthenticatedAccount;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SIMULATED payments for marketplace orders (MARKETPLACE_PURCHASE) and approved repair quotes (REPAIR_BOOKING).
 * No money moves, no gateway is contacted and no card / UPI / bank data is ever accepted or stored.
 *
 * <p>Every attempt, in ONE database transaction with the order / quote row locked:</p>
 * <ol>
 *   <li>The order / quote must belong to the caller (404 otherwise) and be payable (409 otherwise: already paid,
 *       cancelled, expired, quote not approved, superseded, ...).</li>
 *   <li>{@code amountPaise} must equal the amount owed (base + customer fee), else 422 and nothing is recorded.</li>
 *   <li>The attempt is written with a unique {@code TXN-…} id and FROZEN copies of the order's / quote's rates and
 *       amounts.</li>
 *   <li>FAILED / CANCELLED: recorded for the history only. No commission, no payout, no ledger entries; the order /
 *       quote stays payable.</li>
 *   <li>SUCCESS: separate {@link CommissionRecord}, PENDING {@link ProviderPayout} and balanced ledger lines; the
 *       order becomes CONFIRMED. Last, the owning service is told (product-service marks the product SOLD;
 *       repair-service moves the request to PAID). If that call fails everything is rolled back, so there is never
 *       a payment the other service doesn't know about.</li>
 * </ol>
 *
 * <p>Duplicates: a second payment of a paid order / quote is 409 (with the existing {@code transactionId}); the
 * row lock serialises concurrent attempts and the unique {@code settlement_key} is the final guard. An
 * {@code Idempotency-Key} header makes a retried request return the original attempt instead of a new one.</p>
 */
@Service
public class PaymentService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PaymentService.class);

    public static final String REFERENCE_REPAIR_QUOTE = "REPAIR_QUOTE";
    public static final String REFERENCE_PURCHASE_ORDER = "PURCHASE_ORDER";

    static final Set<PaymentStatus> SETTLED = EnumSet.of(PaymentStatus.SUCCESS, PaymentStatus.PARTIALLY_REFUNDED,
            PaymentStatus.REFUNDED);

    private final RepairQuoteRepository quotes;
    private final PurchaseOrderRepository orders;
    private final PaymentTransactionRepository payments;
    private final CommissionRecordRepository commissions;
    private final ProviderPayoutRepository payouts;
    private final LedgerService ledger;
    private final RepairServiceClient repairService;
    private final ProductServiceClient productService;
    private final ReferenceGenerator references;
    private final TransactionViewAssembler views;
    private final Clock clock;

    public PaymentService(RepairQuoteRepository quotes, PurchaseOrderRepository orders,
                          PaymentTransactionRepository payments, CommissionRecordRepository commissions,
                          ProviderPayoutRepository payouts, LedgerService ledger, RepairServiceClient repairService,
                          ProductServiceClient productService, ReferenceGenerator references,
                          TransactionViewAssembler views, Clock clock) {
        this.quotes = quotes;
        this.orders = orders;
        this.payments = payments;
        this.commissions = commissions;
        this.payouts = payouts;
        this.ledger = ledger;
        this.repairService = repairService;
        this.productService = productService;
        this.references = references;
        this.views = views;
        this.clock = clock;
    }

    /** Result of a simulation; {@code replayed} = an earlier attempt returned for a repeated Idempotency-Key. */
    public record Result(TransactionView transaction, boolean replayed) {
    }

    @Transactional
    public Result simulate(AuthenticatedAccount caller, SimulatePaymentRequest req, String idempotencyKey) {
        if ((req.orderId() == null) == (req.repairQuoteId() == null)) {
            throw new BadRequestException("orderId", "send exactly one of orderId (marketplace order) or repairQuoteId (repair quote)");
        }
        String key = normaliseKey(idempotencyKey);
        return req.orderId() != null ? payOrder(caller, req, key) : payQuote(caller, req, key);
    }

    /* ───────────────────────────── marketplace order ───────────────────────────── */

    private Result payOrder(AuthenticatedAccount caller, SimulatePaymentRequest req, String key) {
        PurchaseOrder order = orders.findByIdForUpdate(req.orderId())
                .filter(o -> Objects.equals(o.getBuyerAccountId(), caller.accountId()))
                .orElseThrow(() -> new NotFoundException("Order " + req.orderId() + " not found"));

        Optional<Result> replay = replay(caller, key, p -> Objects.equals(p.getPurchaseOrderId(), order.getId()));
        if (replay.isPresent()) {
            return replay.get();
        }
        payments.findFirstByPurchaseOrderIdAndStatusIn(order.getId(), SETTLED).ifPresent(existing -> {
            throw new ConflictException("Order " + order.getOrderNumber() + " is already paid",
                    Map.of("transactionId", existing.getTransactionId(), "orderStatus", order.getStatus().name()));
        });
        LocalDateTime now = LocalDateTime.now(clock);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            String why = order.getStatus() == OrderStatus.CANCELLED
                    ? "it was cancelled" + (order.getCancelReason() != null ? " (" + order.getCancelReason() + ")" : "")
                    : "it is " + order.getStatus();
            throw new ConflictException("Order " + order.getOrderNumber() + " can't be paid: " + why,
                    Map.of("orderStatus", order.getStatus().name()));
        }
        if (order.getExpiresAt() != null && now.isAfter(order.getExpiresAt())) {
            throw new ConflictException("The payment window for order " + order.getOrderNumber() + " closed at "
                    + order.getExpiresAt() + "; the order will be cancelled and the product released. Place a new order",
                    Map.of("orderStatus", order.getStatus().name(), "expiresAt", order.getExpiresAt().toString()));
        }
        requireAmount(req.amountPaise(), order.getTotalPaise(), "order " + order.getOrderNumber());

        PaymentTransaction p = newAttempt(caller, req, key, now);
        p.setTransactionType(TransactionType.MARKETPLACE_PURCHASE);
        p.setPurchaseOrderId(order.getId());
        p.setProviderId(order.getSellerAccountId());
        p.setProviderAccountId(order.getSellerAccountId());
        p.setProviderType(ProviderType.SELLER);
        p.setBaseAmountPaise(order.getSubtotalPaise());
        p.setCustomerFeeRate(order.getCustomerFeeRate());
        p.setCustomerFeePaise(order.getCustomerFeePaise());
        p.setTotalChargedPaise(order.getTotalPaise());
        p.setProviderCommissionRate(order.getProviderCommissionRate());
        p.setProviderCommissionPaise(order.getProviderCommissionPaise());
        p.setProviderNetPaise(order.getSellerNetPaise());
        p.setPlatformRevenuePaise(order.getPlatformRevenuePaise());
        p.setCurrency(order.getCurrency());

        if (req.outcomeOrDefault() != SimulatedOutcome.SUCCESS) {
            recordUnsuccessful(p, req.outcomeOrDefault(), now);
            order.setFailureReason(p.getFailureReason());
            return new Result(views.view(p), false);
        }

        p.setSettlementKey("ORDER-" + order.getId());
        settle(p, REFERENCE_PURCHASE_ORDER, order.getId(), now);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaidAt(now);
        order.setFailureReason(null);
        orders.flush();

        // last step: a failure here throws and rolls back the payment, commission, payout, ledger and order change
        productService.confirmSale(order.getProductExternalId(), order.getBuyerAccountId(), order.getOrderNumber(),
                p.getTransactionId());
        return new Result(views.view(p), false);
    }

    /* ───────────────────────────── repair quote ───────────────────────────── */

    private Result payQuote(AuthenticatedAccount caller, SimulatePaymentRequest req, String key) {
        RepairQuote quote = quotes.findByIdForUpdate(req.repairQuoteId())
                .filter(q -> Objects.equals(q.getCustomerAccountId(), caller.accountId()))
                .orElseThrow(() -> new NotFoundException("Repair quote " + req.repairQuoteId() + " not found"));

        Optional<Result> replay = replay(caller, key, p -> Objects.equals(p.getRepairQuoteId(), quote.getId()));
        if (replay.isPresent()) {
            return replay.get();
        }
        payments.findFirstByRepairQuoteIdAndStatusIn(quote.getId(), SETTLED).ifPresent(existing -> {
            throw new ConflictException("Quote #" + quote.getQuoteRevision() + " is already paid",
                    Map.of("transactionId", existing.getTransactionId()));
        });
        if (quote.getStatus() != QuoteStatus.APPROVED) {
            String why = switch (quote.getStatus()) {
                case REJECTED -> "rejected quotes can't be paid";
                case SUPERSEDED -> "it was replaced by a revised quote; approve the latest quote first";
                case SENT -> "approve the quote before paying";
                default -> "it is " + quote.getStatus();
            };
            throw new ConflictException("Quote #" + quote.getQuoteRevision() + " can't be paid: " + why,
                    Map.of("quoteStatus", quote.getStatus().name()));
        }
        requireAmount(req.amountPaise(), quote.getTotalPayablePaise(), "quote #" + quote.getQuoteRevision());

        LocalDateTime now = LocalDateTime.now(clock);
        PaymentTransaction p = newAttempt(caller, req, key, now);
        p.setTransactionType(TransactionType.REPAIR_BOOKING);
        p.setRepairQuoteId(quote.getId());
        p.setProviderId(quote.getRepairerProfileId());
        p.setProviderAccountId(quote.getRepairerAccountId());
        p.setProviderType(ProviderType.REPAIRER);
        // frozen copies from the quote: never recomputed with today's rates
        p.setBaseAmountPaise(quote.getBaseAmountPaise());
        p.setCustomerFeeRate(quote.getCustomerFeeRate());
        p.setCustomerFeePaise(quote.getCustomerFeePaise());
        p.setTotalChargedPaise(quote.getTotalPayablePaise());
        p.setProviderCommissionRate(quote.getRepairerCommissionRate());
        p.setProviderCommissionPaise(quote.getRepairerCommissionPaise());
        p.setProviderNetPaise(quote.getRepairerNetPaise());
        p.setPlatformRevenuePaise(quote.getCustomerFeePaise() + quote.getRepairerCommissionPaise());
        p.setCurrency(quote.getCurrency());

        if (req.outcomeOrDefault() != SimulatedOutcome.SUCCESS) {
            recordUnsuccessful(p, req.outcomeOrDefault(), now);
            reportUnsuccessful(quote, p);
            return new Result(views.view(p), false);
        }

        p.setSettlementKey("QUOTE-" + quote.getId());
        settle(p, REFERENCE_REPAIR_QUOTE, quote.getId(), now);

        // last step: a failure here throws and rolls back the payment, commission, payout and ledger
        repairService.confirmPayment(quote.getRepairRequestExternalId(),
                new PaymentConfirmation(quote.getId(), p.getId(), p.getTransactionId(), p.getTotalChargedPaise()));
        return new Result(views.view(p), false);
    }

    /* ───────────────────────────── shared ───────────────────────────── */

    private PaymentTransaction newAttempt(AuthenticatedAccount caller, SimulatePaymentRequest req, String key,
                                          LocalDateTime now) {
        PaymentTransaction p = new PaymentTransaction();
        p.setTransactionId(uniqueTransactionId());
        p.setBuyerAccountId(caller.accountId());
        p.setMethod(req.method());
        p.setIdempotencyKey(key);
        p.setAttemptedAt(now);
        return p;
    }

    private void recordUnsuccessful(PaymentTransaction p, SimulatedOutcome outcome, LocalDateTime now) {
        if (outcome == SimulatedOutcome.CANCELLED) {
            p.setStatus(PaymentStatus.CANCELLED);
            p.setFailureReason("Payment cancelled by the customer (simulated); nothing was charged");
        } else {
            p.setStatus(PaymentStatus.FAILED);
            p.setFailureReason("Payment declined (simulated " + p.getMethod() + " failure); nothing was charged");
        }
        p.setCompletedAt(now);
        payments.saveAndFlush(p);
    }

    /**
     * Tells repair-service about a failed / cancelled repair payment so the request timeline and both dashboards
     * show it. Best effort: nothing was charged, so the attempt is kept (and visible in the customer's payment
     * history) even when repair-service can't be reached; the next attempt or a success brings it up to date.
     */
    private void reportUnsuccessful(RepairQuote quote, PaymentTransaction p) {
        String type = p.getStatus() == PaymentStatus.CANCELLED ? RepairPaymentEvent.CANCELLED : RepairPaymentEvent.FAILED;
        try {
            repairService.reportPaymentEvent(quote.getRepairRequestExternalId(), new RepairPaymentEvent(
                    p.getTransactionId() + ":" + type, type, quote.getId(), p.getId(), p.getTransactionId(),
                    p.getTotalChargedPaise(), 0L, p.getFailureReason()));
        } catch (RuntimeException ex) {
            log.warn("could not report {} payment {} to repair-service: {}", type, p.getTransactionId(), ex.getMessage());
        }
    }

    /** SUCCESS: payment + separate commission + separate payout + balanced ledger lines. */
    private void settle(PaymentTransaction p, String referenceType, Long referenceId, LocalDateTime now) {
        p.setStatus(PaymentStatus.SUCCESS);
        p.setCompletedAt(now);
        payments.saveAndFlush(p);

        CommissionRecord c = new CommissionRecord();
        c.setPaymentTransactionId(p.getId());
        c.setTransactionId(references.next("COM"));
        c.setTransactionType(p.getTransactionType());
        c.setReferenceType(referenceType);
        c.setReferenceId(referenceId);
        c.setCustomerFeeRate(p.getCustomerFeeRate());
        c.setCustomerFeePaise(p.getCustomerFeePaise());
        c.setProviderCommissionRate(p.getProviderCommissionRate());
        c.setProviderCommissionPaise(p.getProviderCommissionPaise());
        c.setTotalCommissionPaise(p.getPlatformRevenuePaise());
        c.setCurrency(p.getCurrency());
        c.setStatus(CommissionStatus.RECORDED);
        commissions.save(c);

        ProviderPayout payout = new ProviderPayout();
        payout.setPayoutReference(references.next("PO"));
        payout.setPaymentTransactionId(p.getId());
        payout.setProviderId(p.getProviderId());
        payout.setProviderType(p.getProviderType());
        payout.setProviderAccountId(p.getProviderAccountId());
        payout.setTransactionType(p.getTransactionType());
        payout.setGrossAmountPaise(p.getBaseAmountPaise());
        payout.setCommissionPaise(p.getProviderCommissionPaise());
        payout.setNetAmountPaise(p.getProviderNetPaise());
        payout.setCurrency(p.getCurrency());
        payout.setStatus(PayoutStatus.PENDING);
        payouts.save(payout);

        ledger.postPayment(p);
        payouts.flush();
    }

    private Optional<Result> replay(AuthenticatedAccount caller, String key,
                                    java.util.function.Predicate<PaymentTransaction> sameTarget) {
        if (key == null) {
            return Optional.empty();
        }
        return payments.findByBuyerAccountIdAndIdempotencyKey(caller.accountId(), key).map(existing -> {
            if (!sameTarget.test(existing)) {
                throw new ConflictException("Idempotency-Key '" + key + "' was already used for a different payment",
                        Map.of("transactionId", existing.getTransactionId()));
            }
            return new Result(views.view(existing), true);
        });
    }

    private static void requireAmount(long sent, long owed, String what) {
        if (sent != owed) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("expectedAmountPaise", owed);
            details.put("receivedAmountPaise", sent);
            throw new UnprocessableException("Incorrect amount: " + what + " requires " + owed + " paise ("
                    + Money.format(owed) + "), received " + sent + " paise. Nothing was charged", details);
        }
    }

    private String uniqueTransactionId() {
        for (int i = 0; i < 5; i++) {
            String id = references.next("TXN");
            if (!payments.existsByTransactionId(id)) {
                return id;
            }
        }
        throw new IllegalStateException("could not generate a unique transaction id");
    }

    private static String normaliseKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String k = key.strip();
        if (k.length() > 100) {
            throw new BadRequestException("Idempotency-Key", "must be at most 100 characters");
        }
        return k;
    }
}
