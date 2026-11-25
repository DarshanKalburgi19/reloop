package com.reloop.orderpayment.service;

import com.reloop.orderpayment.dto.payment.TransactionView;
import com.reloop.orderpayment.dto.refund.RefundRequest;
import com.reloop.orderpayment.entity.CommissionRecord;
import com.reloop.orderpayment.entity.PaymentTransaction;
import com.reloop.orderpayment.entity.ProviderPayout;
import com.reloop.orderpayment.entity.PurchaseOrder;
import com.reloop.orderpayment.entity.RefundRecord;
import com.reloop.orderpayment.enums.CommissionStatus;
import com.reloop.orderpayment.enums.OrderStatus;
import com.reloop.orderpayment.enums.PaymentStatus;
import com.reloop.orderpayment.enums.PayoutStatus;
import com.reloop.orderpayment.enums.RefundStatus;
import com.reloop.orderpayment.exception.BadRequestException;
import com.reloop.orderpayment.exception.ConflictException;
import com.reloop.orderpayment.exception.ForbiddenException;
import com.reloop.orderpayment.exception.NotFoundException;
import com.reloop.orderpayment.repository.CommissionRecordRepository;
import com.reloop.orderpayment.repository.PaymentTransactionRepository;
import com.reloop.orderpayment.repository.ProviderPayoutRepository;
import com.reloop.orderpayment.repository.PurchaseOrderRepository;
import com.reloop.orderpayment.repository.RefundRecordRepository;
import com.reloop.orderpayment.security.AuthenticatedAccount;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SIMULATED refunds of successful payments (nothing is sent back to a real account).
 *
 * <ul>
 *   <li>Who: the provider who received the payment (seller / repairer, via {@code POST /api/transactions/{id}/refunds})
 *       or ReLoop support (internal API). The buyer can't refund their own payment (403).</li>
 *   <li>What: all or part of the base price; the customer fee and provider commission are reversed in proportion
 *       ({@link RefundCalculator}).</li>
 *   <li>Effects, in one transaction with the payment row locked: a COMPLETED {@link RefundRecord}; payment
 *       PARTIALLY_REFUNDED / REFUNDED; commission PARTIALLY_REVERSED / REVERSED; payout PARTIALLY_REVERSED /
 *       REVERSED; order PARTIALLY_REFUNDED / REFUNDED; and ledger lines that reverse the original ones.</li>
 * </ul>
 * Refunds don't change the product (passport ownership stays with the buyer) or the repair request status; returning
 * goods is handled outside the platform in this phase.
 */
@Service
public class RefundService {

    private final PaymentTransactionRepository payments;
    private final CommissionRecordRepository commissions;
    private final ProviderPayoutRepository payouts;
    private final RefundRecordRepository refunds;
    private final PurchaseOrderRepository orders;
    private final LedgerService ledger;
    private final ReferenceGenerator references;
    private final TransactionViewAssembler views;
    private final Clock clock;

    public RefundService(PaymentTransactionRepository payments, CommissionRecordRepository commissions,
                         ProviderPayoutRepository payouts, RefundRecordRepository refunds, PurchaseOrderRepository orders,
                         LedgerService ledger, ReferenceGenerator references, TransactionViewAssembler views, Clock clock) {
        this.payments = payments;
        this.commissions = commissions;
        this.payouts = payouts;
        this.refunds = refunds;
        this.orders = orders;
        this.ledger = ledger;
        this.references = references;
        this.views = views;
        this.clock = clock;
    }

    /** Refund issued by the provider of the payment. */
    @Transactional
    public TransactionView refundAsProvider(AuthenticatedAccount caller, String transactionId, RefundRequest req) {
        PaymentTransaction p = payments.findByTransactionIdForUpdate(transactionId)
                .filter(t -> Objects.equals(t.getBuyerAccountId(), caller.accountId())
                        || Objects.equals(t.getProviderAccountId(), caller.accountId()))
                .orElseThrow(() -> new NotFoundException("Transaction " + transactionId + " not found"));
        if (!Objects.equals(p.getProviderAccountId(), caller.accountId())) {
            throw new ForbiddenException("Only the seller or repairer who received this payment (or ReLoop support) can refund it");
        }
        return refund(p, req, caller.accountId());
    }

    /** Refund issued by ReLoop support / dispute handling (internal API key). */
    @Transactional
    public TransactionView refundAsPlatform(String transactionId, RefundRequest req) {
        PaymentTransaction p = payments.findByTransactionIdForUpdate(transactionId)
                .orElseThrow(() -> new NotFoundException("Transaction " + transactionId + " not found"));
        return refund(p, req, null);
    }

    private TransactionView refund(PaymentTransaction p, RefundRequest req, Long initiatedBy) {
        if (p.getStatus() == PaymentStatus.REFUNDED) {
            throw new ConflictException("Transaction " + p.getTransactionId() + " has already been fully refunded",
                    Map.of("paymentStatus", p.getStatus().name()));
        }
        if (p.getStatus() != PaymentStatus.SUCCESS && p.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new ConflictException("Only successful payments can be refunded; transaction " + p.getTransactionId()
                    + " is " + p.getStatus() + " and nothing was charged", Map.of("paymentStatus", p.getStatus().name()));
        }
        CommissionRecord c = commissions.findByPaymentTransactionId(p.getId())
                .orElseThrow(() -> new IllegalStateException("successful payment without commission record"));
        ProviderPayout payout = payouts.findByPaymentTransactionId(p.getId())
                .orElseThrow(() -> new IllegalStateException("successful payment without payout record"));

        RefundCalculator.Split split;
        try {
            split = RefundCalculator.split(p.getBaseAmountPaise(), p.getCustomerFeePaise(), p.getProviderCommissionPaise(),
                    payout.getRefundedGrossPaise(), c.getReversedCustomerFeePaise(), c.getReversedProviderCommissionPaise(),
                    req.baseAmountPaise());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("baseAmountPaise", ex.getMessage());
        }

        LocalDateTime now = LocalDateTime.now(clock);
        RefundRecord r = new RefundRecord();
        r.setRefundReference(references.next("RFD"));
        r.setPaymentTransactionId(p.getId());
        r.setTransactionType(p.getTransactionType());
        r.setOrderId(p.getPurchaseOrderId());
        r.setRefundAmountPaise(split.customerRefundPaise());
        r.setRefundedBasePaise(split.refundedBasePaise());
        r.setRefundedCustomerFeePaise(split.refundedCustomerFeePaise());
        r.setReversedProviderCommissionPaise(split.reversedProviderCommissionPaise());
        r.setProviderNetReversedPaise(split.providerNetReversedPaise());
        r.setReversedCommissionPaise(split.platformRevenueReversedPaise());
        r.setCurrency(p.getCurrency());
        r.setReason(req.reason());
        r.setNote(req.note() == null || req.note().isBlank() ? null : req.note().strip());
        r.setInitiatedByAccountId(initiatedBy);
        r.setStatus(RefundStatus.COMPLETED);   // simulated: completes instantly
        r.setCompletedAt(now);
        r = refunds.saveAndFlush(r);

        boolean full = split.fullyRefunded();
        p.setRefundedAmountPaise(p.getRefundedAmountPaise() + split.customerRefundPaise());
        p.setStatus(full ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);

        c.setReversedCustomerFeePaise(c.getReversedCustomerFeePaise() + split.refundedCustomerFeePaise());
        c.setReversedProviderCommissionPaise(c.getReversedProviderCommissionPaise() + split.reversedProviderCommissionPaise());
        c.setStatus(full ? CommissionStatus.REVERSED : CommissionStatus.PARTIALLY_REVERSED);

        payout.setRefundedGrossPaise(payout.getRefundedGrossPaise() + split.refundedBasePaise());
        payout.setReversedCommissionPaise(payout.getReversedCommissionPaise() + split.reversedProviderCommissionPaise());
        payout.setReversedNetPaise(payout.getReversedNetPaise() + split.providerNetReversedPaise());
        payout.setStatus(full ? PayoutStatus.REVERSED : PayoutStatus.PARTIALLY_REVERSED);

        if (p.getPurchaseOrderId() != null) {
            PurchaseOrder o = orders.findById(p.getPurchaseOrderId()).orElse(null);
            if (o != null) {
                o.setRefundedAmountPaise(o.getRefundedAmountPaise() + split.customerRefundPaise());
                o.setStatus(full ? OrderStatus.REFUNDED : OrderStatus.PARTIALLY_REFUNDED);
                o.setRefundedAt(now);
            }
        }

        ledger.postRefund(p, r, split);
        payments.flush();
        return views.view(p);
    }
}
