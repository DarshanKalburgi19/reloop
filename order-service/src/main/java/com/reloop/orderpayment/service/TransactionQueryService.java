package com.reloop.orderpayment.service;

import com.reloop.orderpayment.dto.payment.TransactionView;
import com.reloop.orderpayment.dto.report.CustomerPaymentsView;
import com.reloop.orderpayment.dto.report.EarningsTotals;
import com.reloop.orderpayment.dto.report.LedgerEntryView;
import com.reloop.orderpayment.dto.report.PayoutView;
import com.reloop.orderpayment.dto.report.ProviderEarningsView;
import com.reloop.orderpayment.dto.report.ProviderPayoutsView;
import com.reloop.orderpayment.entity.PaymentTransaction;
import com.reloop.orderpayment.entity.ProviderPayout;
import com.reloop.orderpayment.entity.RefundRecord;
import com.reloop.orderpayment.enums.PaymentStatus;
import com.reloop.orderpayment.enums.PayoutStatus;
import com.reloop.orderpayment.enums.TransactionType;
import com.reloop.orderpayment.exception.ForbiddenException;
import com.reloop.orderpayment.exception.NotFoundException;
import com.reloop.orderpayment.repository.PaymentTransactionRepository;
import com.reloop.orderpayment.repository.ProviderPayoutRepository;
import com.reloop.orderpayment.repository.RefundRecordRepository;
import com.reloop.orderpayment.security.AuthenticatedAccount;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side shared by the customer, seller and repairer dashboards. Every number comes from the same payment,
 * commission, payout and refund rows, so all three dashboards always agree.
 *
 * <p>Access: a transaction is visible to its buyer and to its provider (seller / repairer account); anybody else
 * gets 404. {@code /api/customers/{id}/…} and {@code /api/providers/{id}/…} only work for {@code id} = the
 * caller's own account id (403 otherwise).</p>
 */
@Service
public class TransactionQueryService {

    private final PaymentTransactionRepository payments;
    private final ProviderPayoutRepository payouts;
    private final RefundRecordRepository refunds;
    private final LedgerService ledger;
    private final TransactionViewAssembler views;

    public TransactionQueryService(PaymentTransactionRepository payments, ProviderPayoutRepository payouts,
                                   RefundRecordRepository refunds, LedgerService ledger, TransactionViewAssembler views) {
        this.payments = payments;
        this.payouts = payouts;
        this.refunds = refunds;
        this.ledger = ledger;
        this.views = views;
    }

    /** The payment row if the caller is its buyer or provider, else 404. */
    @Transactional(readOnly = true)
    public PaymentTransaction visible(AuthenticatedAccount caller, String transactionId) {
        return payments.findByTransactionId(transactionId)
                .filter(p -> Objects.equals(p.getBuyerAccountId(), caller.accountId())
                        || Objects.equals(p.getProviderAccountId(), caller.accountId()))
                .orElseThrow(() -> new NotFoundException("Transaction " + transactionId + " not found"));
    }

    @Transactional(readOnly = true)
    public TransactionView transaction(AuthenticatedAccount caller, String transactionId) {
        return views.view(visible(caller, transactionId));
    }

    /** Ledger lines of one visible transaction. */
    @Transactional(readOnly = true)
    public List<LedgerEntryView> ledger(AuthenticatedAccount caller, String transactionId) {
        PaymentTransaction p = visible(caller, transactionId);
        Map<Long, String> refundRefs = refunds.findByPaymentTransactionIdOrderByIdAsc(p.getId()).stream()
                .collect(Collectors.toMap(RefundRecord::getId, RefundRecord::getRefundReference));
        return ledger.entriesOf(p.getId()).stream()
                .map(e -> LedgerEntryView.of(e, e.getRefundRecordId() == null ? null : refundRefs.get(e.getRefundRecordId())))
                .toList();
    }

    /** Customer dashboard: all of the customer's payment attempts, newest first, with totals. */
    @Transactional(readOnly = true)
    public CustomerPaymentsView customerPayments(AuthenticatedAccount caller, Long customerId) {
        requireSelf(caller, customerId, "payments");
        List<PaymentTransaction> list = payments.findByBuyerAccountIdOrderByIdDesc(customerId);
        long paid = 0;
        long fees = 0;
        long refunded = 0;
        int ok = 0;
        int failed = 0;
        int cancelled = 0;
        for (PaymentTransaction p : list) {
            if (p.getStatus().isSettled()) {
                ok++;
                paid += p.getTotalChargedPaise();
                fees += p.getCustomerFeePaise();
                refunded += p.getRefundedAmountPaise();
            } else if (p.getStatus() == PaymentStatus.FAILED) {
                failed++;
            } else if (p.getStatus() == PaymentStatus.CANCELLED) {
                cancelled++;
            }
        }
        return new CustomerPaymentsView(customerId, list.size(), ok, failed, cancelled, paid, fees, refunded,
                paid - refunded, Money.CURRENCY, list.stream().map(views::view).toList());
    }

    /** Seller / repairer dashboard: earnings from successful payments (failed attempts never create payouts). */
    @Transactional(readOnly = true)
    public ProviderEarningsView providerEarnings(AuthenticatedAccount caller, Long providerAccountId) {
        requireSelf(caller, providerAccountId, "earnings");
        List<ProviderPayout> list = payouts.findByProviderAccountIdOrderByIdDesc(providerAccountId);
        Map<Long, String> txIds = transactionIds(list);
        Map<String, EarningsTotals> byType = new LinkedHashMap<>();
        for (TransactionType type : TransactionType.values()) {
            byType.put(type.name(), totals(list.stream().filter(p -> p.getTransactionType() == type).toList()));
        }
        return new ProviderEarningsView(providerAccountId, Money.CURRENCY, totals(list), byType,
                list.stream().map(p -> PayoutView.of(p, txIds.get(p.getPaymentTransactionId()))).toList());
    }

    @Transactional(readOnly = true)
    public ProviderPayoutsView providerPayouts(AuthenticatedAccount caller, Long providerAccountId) {
        requireSelf(caller, providerAccountId, "payouts");
        List<ProviderPayout> list = payouts.findByProviderAccountIdOrderByIdDesc(providerAccountId);
        Map<Long, String> txIds = transactionIds(list);
        EarningsTotals t = totals(list);
        return new ProviderPayoutsView(providerAccountId, list.size(), t.pendingPayoutPaise(), t.paidOutPaise(),
                list.stream().map(p -> PayoutView.of(p, txIds.get(p.getPaymentTransactionId()))).toList());
    }

    static EarningsTotals totals(List<ProviderPayout> list) {
        long gross = 0;
        long commission = 0;
        long net = 0;
        long refundedGross = 0;
        long reversedCommission = 0;
        long reversedNet = 0;
        long pending = 0;
        long paidOut = 0;
        for (ProviderPayout p : list) {
            gross += p.getGrossAmountPaise();
            commission += p.getCommissionPaise();
            net += p.getNetAmountPaise();
            refundedGross += p.getRefundedGrossPaise();
            reversedCommission += p.getReversedCommissionPaise();
            reversedNet += p.getReversedNetPaise();
            if (p.getStatus() == PayoutStatus.PAID) {
                paidOut += p.currentNetPaise();
            } else if (p.getStatus() != PayoutStatus.REVERSED) {
                pending += p.currentNetPaise();
            }
        }
        return new EarningsTotals(list.size(), gross, commission, net, refundedGross, reversedCommission, reversedNet,
                net - reversedNet, pending, paidOut);
    }

    private Map<Long, String> transactionIds(List<ProviderPayout> list) {
        return payments.findAllById(list.stream().map(ProviderPayout::getPaymentTransactionId).toList()).stream()
                .collect(Collectors.toMap(PaymentTransaction::getId, PaymentTransaction::getTransactionId,
                        (a, b) -> a));
    }

    private static void requireSelf(AuthenticatedAccount caller, Long id, String what) {
        if (!Objects.equals(caller.accountId(), id)) {
            throw new ForbiddenException("You can only view your own " + what);
        }
    }

}
