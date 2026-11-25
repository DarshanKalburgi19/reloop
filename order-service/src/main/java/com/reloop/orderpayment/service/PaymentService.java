package com.reloop.orderpayment.service;

import com.reloop.orderpayment.client.PaymentConfirmation;
import com.reloop.orderpayment.client.RepairServiceClient;
import com.reloop.orderpayment.dto.payment.PaymentView;
import com.reloop.orderpayment.dto.payment.SimulatePaymentRequest;
import com.reloop.orderpayment.dto.payment.SimulatedOutcome;
import com.reloop.orderpayment.entity.CommissionRecord;
import com.reloop.orderpayment.entity.PaymentTransaction;
import com.reloop.orderpayment.entity.ProviderPayout;
import com.reloop.orderpayment.entity.RepairQuote;
import com.reloop.orderpayment.enums.CommissionStatus;
import com.reloop.orderpayment.enums.PaymentMethod;
import com.reloop.orderpayment.enums.PaymentStatus;
import com.reloop.orderpayment.enums.PayoutStatus;
import com.reloop.orderpayment.enums.ProviderType;
import com.reloop.orderpayment.enums.QuoteStatus;
import com.reloop.orderpayment.exception.ConflictException;
import com.reloop.orderpayment.exception.NotFoundException;
import com.reloop.orderpayment.repository.CommissionRecordRepository;
import com.reloop.orderpayment.repository.PaymentTransactionRepository;
import com.reloop.orderpayment.repository.ProviderPayoutRepository;
import com.reloop.orderpayment.repository.RepairQuoteRepository;
import com.reloop.orderpayment.security.AuthenticatedAccount;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SIMULATED payments for approved repair quotes. No money moves and no card/UPI/bank data is ever accepted.
 *
 * <p>Successful payment, in ONE database transaction:</p>
 * <ol>
 *   <li>checks the quote belongs to the caller, is APPROVED (rejected, superseded or unapproved quotes → 409)
 *       and isn't already paid;</li>
 *   <li>writes a SUCCESS {@link PaymentTransaction} copying the quote's FROZEN amounts and rates;</li>
 *   <li>writes a separate {@link CommissionRecord} (customer fee + repairer commission) and a separate
 *       {@link ProviderPayout} (repair price − commission, PENDING);</li>
 *   <li>calls repair-service so the repair request moves AWAITING_PAYMENT → PAID. If that call fails, the
 *       exception rolls everything back, so no payment exists that repair-service doesn't know about.</li>
 * </ol>
 * A simulated FAILURE only records a FAILURE transaction; the quote stays payable.
 */
@Service
public class PaymentService {

    public static final String REFERENCE_REPAIR_QUOTE = "REPAIR_QUOTE";

    private final RepairQuoteRepository quotes;
    private final PaymentTransactionRepository payments;
    private final CommissionRecordRepository commissions;
    private final ProviderPayoutRepository payouts;
    private final RepairServiceClient repairService;
    private final ReferenceGenerator references;
    private final Clock clock;

    public PaymentService(RepairQuoteRepository quotes, PaymentTransactionRepository payments,
                          CommissionRecordRepository commissions, ProviderPayoutRepository payouts,
                          RepairServiceClient repairService, ReferenceGenerator references, Clock clock) {
        this.quotes = quotes;
        this.payments = payments;
        this.commissions = commissions;
        this.payouts = payouts;
        this.repairService = repairService;
        this.references = references;
        this.clock = clock;
    }

    @Transactional
    public PaymentView simulateRepairPayment(AuthenticatedAccount caller, SimulatePaymentRequest req) {
        RepairQuote quote = quotes.findById(req.repairQuoteId())
                .filter(q -> Objects.equals(q.getCustomerAccountId(), caller.accountId()))
                .orElseThrow(() -> new NotFoundException("Repair quote " + req.repairQuoteId() + " not found"));

        payments.findFirstByRepairQuoteIdAndStatus(quote.getId(), PaymentStatus.SUCCESS).ifPresent(existing -> {
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

        LocalDateTime now = LocalDateTime.now(clock);
        PaymentTransaction p = new PaymentTransaction();
        p.setTransactionId(references.next("PAY"));
        p.setRepairQuoteId(quote.getId());
        p.setBuyerAccountId(caller.accountId());
        p.setProviderId(quote.getRepairerProfileId());
        p.setProviderType(ProviderType.REPAIRER);
        p.setMethod(req.method() != null ? req.method() : PaymentMethod.UPI);
        // frozen copies from the quote: never recomputed with today's rates
        p.setBaseAmountPaise(quote.getBaseAmountPaise());
        p.setCustomerFeeRate(quote.getCustomerFeeRate());
        p.setCustomerFeePaise(quote.getCustomerFeePaise());
        p.setTotalChargedPaise(quote.getTotalPayablePaise());
        p.setRepairerCommissionRate(quote.getRepairerCommissionRate());
        p.setRepairerCommissionPaise(quote.getRepairerCommissionPaise());
        p.setProviderNetPaise(quote.getRepairerNetPaise());
        p.setCurrency(quote.getCurrency());
        p.setAttemptedAt(now);

        if (req.outcome() == SimulatedOutcome.FAILURE) {
            p.setStatus(PaymentStatus.FAILURE);
            p.setFailureReason("Simulated decline (demo)");
            return PaymentView.of(payments.save(p), quote.getRepairRequestExternalId(), null, null);
        }

        p.setStatus(PaymentStatus.SUCCESS);
        p.setCompletedAt(now);
        p = payments.saveAndFlush(p);

        CommissionRecord c = new CommissionRecord();
        c.setPaymentTransactionId(p.getId());
        c.setTransactionId(references.next("COM"));
        c.setReferenceType(REFERENCE_REPAIR_QUOTE);
        c.setReferenceId(quote.getId());
        c.setCustomerFeeRate(p.getCustomerFeeRate());
        c.setCustomerFeePaise(p.getCustomerFeePaise());
        c.setProviderCommissionRate(p.getRepairerCommissionRate());
        c.setProviderCommissionPaise(p.getRepairerCommissionPaise());
        c.setTotalCommissionPaise(p.getCustomerFeePaise() + p.getRepairerCommissionPaise());
        c.setCurrency(p.getCurrency());
        c.setStatus(CommissionStatus.RECORDED);
        c = commissions.save(c);

        ProviderPayout payout = new ProviderPayout();
        payout.setPayoutReference(references.next("PO"));
        payout.setPaymentTransactionId(p.getId());
        payout.setProviderId(quote.getRepairerProfileId());
        payout.setProviderType(ProviderType.REPAIRER);
        payout.setGrossAmountPaise(p.getBaseAmountPaise());
        payout.setCommissionPaise(p.getRepairerCommissionPaise());
        payout.setNetAmountPaise(p.getProviderNetPaise());
        payout.setCurrency(p.getCurrency());
        payout.setStatus(PayoutStatus.PENDING);
        payout = payouts.saveAndFlush(payout);

        // last step: a failure here throws and rolls back the payment, commission and payout
        repairService.confirmPayment(quote.getRepairRequestExternalId(),
                new PaymentConfirmation(quote.getId(), p.getId(), p.getTransactionId(), p.getTotalChargedPaise()));

        return PaymentView.of(p, quote.getRepairRequestExternalId(), c, payout);
    }

    /** One of the caller's own payments by its {@code PAY-…} reference. */
    @Transactional(readOnly = true)
    public PaymentView get(AuthenticatedAccount caller, String transactionId) {
        PaymentTransaction p = payments.findByTransactionId(transactionId)
                .filter(t -> Objects.equals(t.getBuyerAccountId(), caller.accountId()))
                .orElseThrow(() -> new NotFoundException("Payment " + transactionId + " not found"));
        Long requestId = p.getRepairQuoteId() == null ? null
                : quotes.findById(p.getRepairQuoteId()).map(RepairQuote::getRepairRequestExternalId).orElse(null);
        return PaymentView.of(p, requestId,
                commissions.findByPaymentTransactionId(p.getId()).orElse(null),
                payouts.findByPaymentTransactionId(p.getId()).orElse(null));
    }
}
