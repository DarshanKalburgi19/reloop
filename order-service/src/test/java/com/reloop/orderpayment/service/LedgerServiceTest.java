package com.reloop.orderpayment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.reloop.orderpayment.entity.LedgerEntry;
import com.reloop.orderpayment.entity.PaymentTransaction;
import com.reloop.orderpayment.entity.RefundRecord;
import com.reloop.orderpayment.enums.LedgerAccount;
import com.reloop.orderpayment.enums.LedgerDirection;
import com.reloop.orderpayment.enums.LedgerEntryType;
import com.reloop.orderpayment.enums.ProviderType;
import com.reloop.orderpayment.enums.TransactionType;
import com.reloop.orderpayment.repository.LedgerEntryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Double-entry ledger: a payment balances, and refunds post exact opposite entries linked to the originals. */
class LedgerServiceTest {

    private final LedgerEntryRepository repo = mock(LedgerEntryRepository.class);
    private final List<LedgerEntry> stored = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong();
    private final LedgerService ledger = new LedgerService(repo);

    private PaymentTransaction payment;

    @BeforeEach
    void setUp() {
        when(repo.saveAll(any())).thenAnswer(inv -> {
            Iterable<LedgerEntry> lines = inv.getArgument(0);
            List<LedgerEntry> saved = new ArrayList<>();
            for (LedgerEntry e : lines) {
                ReflectionTestUtils.setField(e, "id", ids.incrementAndGet());
                stored.add(e);
                saved.add(e);
            }
            return saved;
        });
        when(repo.findByPaymentTransactionIdOrderByIdAsc(anyLong())).thenAnswer(inv -> List.copyOf(stored));
        when(repo.findByPaymentTransactionIdAndEntryTypeOrderByIdAsc(anyLong(), eq(LedgerEntryType.PAYMENT)))
                .thenAnswer(inv -> stored.stream().filter(e -> e.getEntryType() == LedgerEntryType.PAYMENT).toList());

        payment = new PaymentTransaction();
        ReflectionTestUtils.setField(payment, "id", 9L);
        payment.setTransactionId("TXN-20261125-AAAAAAAAAA");
        payment.setTransactionType(TransactionType.MARKETPLACE_PURCHASE);
        payment.setBuyerAccountId(501L);
        payment.setProviderAccountId(701L);
        payment.setProviderType(ProviderType.SELLER);
        payment.setTotalChargedPaise(102_000L);
        payment.setCustomerFeePaise(2_000L);
        payment.setProviderCommissionPaise(2_000L);
        payment.setProviderNetPaise(98_000L);
    }

    private RefundRecord refund(long id) {
        RefundRecord r = new RefundRecord();
        ReflectionTestUtils.setField(r, "id", id);
        r.setRefundReference("RFD-20261125-BBBBBBBBB" + id);
        return r;
    }

    @Test
    void paymentLinesBalance() {
        List<LedgerEntry> lines = ledger.postPayment(payment);

        assertThat(lines).hasSize(4);
        long debit = lines.stream().filter(e -> e.getDirection() == LedgerDirection.DEBIT).mapToLong(LedgerEntry::getAmountPaise).sum();
        long credit = lines.stream().filter(e -> e.getDirection() == LedgerDirection.CREDIT).mapToLong(LedgerEntry::getAmountPaise).sum();
        assertThat(debit).isEqualTo(credit).isEqualTo(102_000);

        Map<LedgerAccount, Long> balances = ledger.balances(9L);
        assertThat(balances).containsEntry(LedgerAccount.PROVIDER_PAYABLE, 98_000L)
                .containsEntry(LedgerAccount.PLATFORM_FEE_REVENUE, 2_000L)
                .containsEntry(LedgerAccount.PLATFORM_COMMISSION_REVENUE, 2_000L)
                .containsEntry(LedgerAccount.CUSTOMER_PAYMENTS, -102_000L);
    }

    @Test
    void refundsReverseEachAccountAndFullRefundNetsToZero() {
        ledger.postPayment(payment);

        RefundCalculator.Split partial = RefundCalculator.split(100_000, 2_000, 2_000, 0, 0, 0, 25_000L);
        List<LedgerEntry> reversal = ledger.postRefund(payment, refund(1), partial);
        assertThat(reversal).hasSize(4).allSatisfy(e -> {
            assertThat(e.getEntryType()).isEqualTo(LedgerEntryType.REFUND);
            assertThat(e.getRefundRecordId()).isEqualTo(1L);
            LedgerEntry original = stored.stream().filter(o -> o.getId().equals(e.getReversalOfEntryId())).findFirst().orElseThrow();
            assertThat(original.getAccount()).isEqualTo(e.getAccount());
            assertThat(original.getDirection()).isEqualTo(e.getDirection().opposite());
        });
        Map<LedgerAccount, Long> afterPartial = ledger.balances(9L);
        assertThat(afterPartial).containsEntry(LedgerAccount.PROVIDER_PAYABLE, 73_500L)
                .containsEntry(LedgerAccount.CUSTOMER_PAYMENTS, -76_500L);
        assertThat(afterPartial.values().stream().mapToLong(Long::longValue).sum()).isZero();   // still balanced

        RefundCalculator.Split rest = RefundCalculator.split(100_000, 2_000, 2_000, 25_000, 500, 500, null);
        ledger.postRefund(payment, refund(2), rest);
        assertThat(ledger.balances(9L).values()).allMatch(v -> v == 0L);
        assertThat(stored).hasSize(12);
    }

    @Test
    void zeroAmountLinesAreSkipped() {
        payment.setCustomerFeePaise(0L);
        payment.setTotalChargedPaise(100_000L);
        assertThat(ledger.postPayment(payment)).extracting(LedgerEntry::getAccount)
                .containsExactly(LedgerAccount.CUSTOMER_PAYMENTS, LedgerAccount.PROVIDER_PAYABLE,
                        LedgerAccount.PLATFORM_COMMISSION_REVENUE);
    }
}
