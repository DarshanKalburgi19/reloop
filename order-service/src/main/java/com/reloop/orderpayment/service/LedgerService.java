package com.reloop.orderpayment.service;

import com.reloop.orderpayment.entity.LedgerEntry;
import com.reloop.orderpayment.entity.PaymentTransaction;
import com.reloop.orderpayment.entity.RefundRecord;
import com.reloop.orderpayment.enums.LedgerAccount;
import com.reloop.orderpayment.enums.LedgerDirection;
import com.reloop.orderpayment.enums.LedgerEntryType;
import com.reloop.orderpayment.repository.LedgerEntryRepository;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Simulated double-entry ledger (append-only; lines are never updated or deleted).
 *
 * <pre>
 * SUCCESS payment                                   refund (proportional part)
 * DEBIT  CUSTOMER_PAYMENTS           total          CREDIT CUSTOMER_PAYMENTS           customer refund
 * CREDIT PROVIDER_PAYABLE            provider net   DEBIT  PROVIDER_PAYABLE            provider net reversed
 * CREDIT PLATFORM_FEE_REVENUE        customer fee   DEBIT  PLATFORM_FEE_REVENUE        customer fee refunded
 * CREDIT PLATFORM_COMMISSION_REVENUE commission     DEBIT  PLATFORM_COMMISSION_REVENUE commission reversed
 * </pre>
 * Every posting balances (debits = credits). Refund lines point at the payment line they reverse
 * ({@code reversalOfEntryId}); after a full refund each account nets to zero for that payment. Zero-amount lines
 * (e.g. a 0% fee) are not written.
 */
@Service
public class LedgerService {

    private final LedgerEntryRepository entries;

    public LedgerService(LedgerEntryRepository entries) {
        this.entries = entries;
    }

    /** Posts the four PAYMENT lines of a SUCCESS payment. */
    public List<LedgerEntry> postPayment(PaymentTransaction p) {
        List<LedgerEntry> lines = new ArrayList<>();
        add(lines, p, null, LedgerEntryType.PAYMENT, LedgerAccount.CUSTOMER_PAYMENTS, LedgerDirection.DEBIT,
                p.getTotalChargedPaise(), p.getBuyerAccountId(), null, "Customer paid (simulated)");
        add(lines, p, null, LedgerEntryType.PAYMENT, LedgerAccount.PROVIDER_PAYABLE, LedgerDirection.CREDIT,
                p.getProviderNetPaise(), p.getProviderAccountId(), null, "Owed to " + p.getProviderType().name().toLowerCase());
        add(lines, p, null, LedgerEntryType.PAYMENT, LedgerAccount.PLATFORM_FEE_REVENUE, LedgerDirection.CREDIT,
                p.getCustomerFeePaise(), null, null, "Customer platform fee");
        add(lines, p, null, LedgerEntryType.PAYMENT, LedgerAccount.PLATFORM_COMMISSION_REVENUE, LedgerDirection.CREDIT,
                p.getProviderCommissionPaise(), null, null, "Provider commission");
        return entries.saveAll(lines);
    }

    /** Posts the REFUND lines reversing {@code split} of the payment, each linked to the original PAYMENT line. */
    public List<LedgerEntry> postRefund(PaymentTransaction p, RefundRecord refund, RefundCalculator.Split split) {
        Map<LedgerAccount, LedgerEntry> original = new EnumMap<>(LedgerAccount.class);
        for (LedgerEntry e : entries.findByPaymentTransactionIdAndEntryTypeOrderByIdAsc(p.getId(), LedgerEntryType.PAYMENT)) {
            original.put(e.getAccount(), e);
        }
        List<LedgerEntry> lines = new ArrayList<>();
        reverse(lines, p, refund, original, LedgerAccount.CUSTOMER_PAYMENTS, split.customerRefundPaise(),
                p.getBuyerAccountId(), "Refund to customer (simulated)");
        reverse(lines, p, refund, original, LedgerAccount.PROVIDER_PAYABLE, split.providerNetReversedPaise(),
                p.getProviderAccountId(), "Provider earnings reversed");
        reverse(lines, p, refund, original, LedgerAccount.PLATFORM_FEE_REVENUE, split.refundedCustomerFeePaise(),
                null, "Customer fee refunded");
        reverse(lines, p, refund, original, LedgerAccount.PLATFORM_COMMISSION_REVENUE,
                split.reversedProviderCommissionPaise(), null, "Provider commission reversed");
        return entries.saveAll(lines);
    }

    public List<LedgerEntry> entriesOf(Long paymentId) {
        return entries.findByPaymentTransactionIdOrderByIdAsc(paymentId);
    }

    /** Net balance per account for one payment (credits positive, debits negative). */
    public Map<LedgerAccount, Long> balances(Long paymentId) {
        Map<LedgerAccount, Long> sums = new EnumMap<>(LedgerAccount.class);
        for (LedgerEntry e : entriesOf(paymentId)) {
            sums.merge(e.getAccount(), e.signedCreditPaise(), Long::sum);
        }
        return sums;
    }

    private static void reverse(List<LedgerEntry> lines, PaymentTransaction p, RefundRecord refund,
                                Map<LedgerAccount, LedgerEntry> original, LedgerAccount account, long amount,
                                Long party, String description) {
        LedgerEntry orig = original.get(account);
        LedgerDirection direction = orig != null ? orig.getDirection().opposite() : defaultPaymentDirection(account).opposite();
        add(lines, p, refund, LedgerEntryType.REFUND, account, direction, amount, party,
                orig != null ? orig.getId() : null, description + " " + refund.getRefundReference());
    }

    private static LedgerDirection defaultPaymentDirection(LedgerAccount account) {
        return account == LedgerAccount.CUSTOMER_PAYMENTS ? LedgerDirection.DEBIT : LedgerDirection.CREDIT;
    }

    private static void add(List<LedgerEntry> lines, PaymentTransaction p, RefundRecord refund, LedgerEntryType type,
                            LedgerAccount account, LedgerDirection direction, long amount, Long party,
                            Long reversalOf, String description) {
        if (amount <= 0) {
            return;
        }
        LedgerEntry e = new LedgerEntry();
        e.setPaymentTransactionId(p.getId());
        e.setTransactionReference(p.getTransactionId());
        e.setRefundRecordId(refund == null ? null : refund.getId());
        e.setTransactionType(p.getTransactionType());
        e.setEntryType(type);
        e.setAccount(account);
        e.setDirection(direction);
        e.setAmountPaise(amount);
        e.setPartyAccountId(party);
        e.setReversalOfEntryId(reversalOf);
        e.setDescription(description.length() > 200 ? description.substring(0, 200) : description);
        e.setCurrency(p.getCurrency());
        lines.add(e);
    }
}
