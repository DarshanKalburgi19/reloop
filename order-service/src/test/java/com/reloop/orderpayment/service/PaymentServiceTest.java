package com.reloop.orderpayment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reloop.orderpayment.client.PaymentConfirmation;
import com.reloop.orderpayment.client.ProductServiceClient;
import com.reloop.orderpayment.client.RepairServiceClient;
import com.reloop.orderpayment.dto.payment.SimulatePaymentRequest;
import com.reloop.orderpayment.dto.payment.SimulatedOutcome;
import com.reloop.orderpayment.dto.payment.TransactionView;
import com.reloop.orderpayment.entity.CommissionRecord;
import com.reloop.orderpayment.entity.LedgerEntry;
import com.reloop.orderpayment.entity.PaymentTransaction;
import com.reloop.orderpayment.entity.ProviderPayout;
import com.reloop.orderpayment.entity.PurchaseOrder;
import com.reloop.orderpayment.entity.RepairQuote;
import com.reloop.orderpayment.enums.CommissionStatus;
import com.reloop.orderpayment.enums.LedgerAccount;
import com.reloop.orderpayment.enums.LedgerDirection;
import com.reloop.orderpayment.enums.OrderStatus;
import com.reloop.orderpayment.enums.PaymentMethod;
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
import com.reloop.orderpayment.repository.LedgerEntryRepository;
import com.reloop.orderpayment.repository.PaymentTransactionRepository;
import com.reloop.orderpayment.repository.ProviderPayoutRepository;
import com.reloop.orderpayment.repository.PurchaseOrderRepository;
import com.reloop.orderpayment.repository.RefundRecordRepository;
import com.reloop.orderpayment.repository.RepairQuoteRepository;
import com.reloop.orderpayment.security.AuthenticatedAccount;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pure unit tests (Mockito, no Spring, no database) of the payment simulation rules: successful, failed and
 * cancelled payments, incorrect amounts, duplicate transactions, frozen commission and provider net earnings.
 */
class PaymentServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC);
    private static final AuthenticatedAccount BUYER = new AuthenticatedAccount(501L, "buyer@reloop.test", "CUSTOMER");
    private static final long SELLER = 701L;

    private final RepairQuoteRepository quotes = mock(RepairQuoteRepository.class);
    private final PurchaseOrderRepository orders = mock(PurchaseOrderRepository.class);
    private final PaymentTransactionRepository payments = mock(PaymentTransactionRepository.class);
    private final CommissionRecordRepository commissions = mock(CommissionRecordRepository.class);
    private final ProviderPayoutRepository payouts = mock(ProviderPayoutRepository.class);
    private final RefundRecordRepository refunds = mock(RefundRecordRepository.class);
    private final LedgerEntryRepository ledgerEntries = mock(LedgerEntryRepository.class);
    private final RepairServiceClient repairService = mock(RepairServiceClient.class);
    private final ProductServiceClient productService = mock(ProductServiceClient.class);

    private final List<PaymentTransaction> savedPayments = new ArrayList<>();
    private final List<CommissionRecord> savedCommissions = new ArrayList<>();
    private final List<ProviderPayout> savedPayouts = new ArrayList<>();
    private final List<LedgerEntry> savedLedger = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(100);

    private PaymentService service;

    @BeforeEach
    void setUp() {
        when(payments.saveAndFlush(any(PaymentTransaction.class))).thenAnswer(inv -> {
            PaymentTransaction p = inv.getArgument(0);
            if (p.getId() == null) {
                ReflectionTestUtils.setField(p, "id", ids.incrementAndGet());
                savedPayments.add(p);
            }
            return p;
        });
        when(commissions.save(any(CommissionRecord.class))).thenAnswer(inv -> {
            savedCommissions.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(payouts.save(any(ProviderPayout.class))).thenAnswer(inv -> {
            savedPayouts.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(ledgerEntries.saveAll(any())).thenAnswer(inv -> {
            Iterable<LedgerEntry> lines = inv.getArgument(0);
            lines.forEach(savedLedger::add);
            return savedLedger;
        });
        // the assembler reads commission/payout back by payment id: answer from what was "saved"
        when(commissions.findByPaymentTransactionId(anyLong())).thenAnswer(inv -> savedCommissions.stream()
                .filter(c -> c.getPaymentTransactionId().equals(inv.getArgument(0))).findFirst());
        when(payouts.findByPaymentTransactionId(anyLong())).thenAnswer(inv -> savedPayouts.stream()
                .filter(p -> p.getPaymentTransactionId().equals(inv.getArgument(0))).findFirst());

        TransactionViewAssembler views = new TransactionViewAssembler(orders, quotes, commissions, payouts, refunds);
        service = new PaymentService(quotes, orders, payments, commissions, payouts, new LedgerService(ledgerEntries),
                repairService, productService, new ReferenceGenerator(CLOCK), views, CLOCK);
    }

    /** The user's example: ₹1,000 item, 2% + 2%. */
    private PurchaseOrder order() {
        PurchaseOrder o = new PurchaseOrder();
        ReflectionTestUtils.setField(o, "id", 11L);
        o.setOrderNumber("ORD-20261125-ABCDEFGHJK");
        o.setBuyerAccountId(BUYER.accountId());
        o.setSellerAccountId(SELLER);
        o.setProductExternalId(42L);
        o.setStatus(OrderStatus.PENDING_PAYMENT);
        FeeCalculator.Breakdown b = FeeCalculator.compute(100_000, new BigDecimal("0.02"), new BigDecimal("0.02"));
        o.setSubtotalPaise(b.baseAmountPaise());
        o.setCustomerFeeRate(b.customerFeeRate());
        o.setCustomerFeePaise(b.customerFeePaise());
        o.setTotalPaise(b.totalPayablePaise());
        o.setProviderCommissionRate(b.providerCommissionRate());
        o.setProviderCommissionPaise(b.providerCommissionPaise());
        o.setSellerNetPaise(b.providerNetPaise());
        o.setPlatformRevenuePaise(b.platformRevenuePaise());
        o.setExpiresAt(LocalDateTime.now(CLOCK).plusMinutes(30));
        when(orders.findByIdForUpdate(11L)).thenReturn(Optional.of(o));
        when(orders.findById(11L)).thenReturn(Optional.of(o));
        return o;
    }

    private static SimulatePaymentRequest payOrder(PaymentMethod method, SimulatedOutcome outcome, long amount) {
        return new SimulatePaymentRequest(11L, null, method, outcome, amount);
    }

    /* ─────────────────────────── successful payment ─────────────────────────── */

    @Test
    void successfulPaymentConfirmsTheOrderAndCreatesSeparateCommissionAndPayout() {
        PurchaseOrder o = order();

        TransactionView t = service.simulate(BUYER, payOrder(PaymentMethod.UPI, SimulatedOutcome.SUCCESS, 102_000), null)
                .transaction();

        assertThat(t.status()).isEqualTo("SUCCESS");
        assertThat(t.transactionType()).isEqualTo("MARKETPLACE_PURCHASE");
        assertThat(t.transactionId()).matches("TXN-20261125-[0-9A-Z]{10}");
        assertThat(t.simulated()).isTrue();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(o.getPaidAt()).isEqualTo(LocalDateTime.now(CLOCK));

        PaymentTransaction p = savedPayments.get(0);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(p.getProviderType()).isEqualTo(ProviderType.SELLER);
        assertThat(p.getProviderAccountId()).isEqualTo(SELLER);
        assertThat(p.getSettlementKey()).isEqualTo("ORDER-11");

        assertThat(savedCommissions).singleElement().satisfies(c -> {
            assertThat(c.getTransactionId()).startsWith("COM-");
            assertThat(c.getCustomerFeePaise()).isEqualTo(2_000);
            assertThat(c.getProviderCommissionPaise()).isEqualTo(2_000);
            assertThat(c.getTotalCommissionPaise()).isEqualTo(4_000);
            assertThat(c.getStatus()).isEqualTo(CommissionStatus.RECORDED);
        });
        assertThat(savedPayouts).singleElement().satisfies(po -> {
            assertThat(po.getPayoutReference()).startsWith("PO-");
            assertThat(po.getGrossAmountPaise()).isEqualTo(100_000);
            assertThat(po.getNetAmountPaise()).isEqualTo(98_000);
            assertThat(po.getStatus()).isEqualTo(PayoutStatus.PENDING);
        });
        verify(productService).confirmSale(42L, 501L, "ORD-20261125-ABCDEFGHJK", t.transactionId());
    }

    @Test
    void successfulPaymentPostsABalancedLedger() {
        order();
        service.simulate(BUYER, payOrder(PaymentMethod.CARD, SimulatedOutcome.SUCCESS, 102_000), null);

        long debits = savedLedger.stream().filter(e -> e.getDirection() == LedgerDirection.DEBIT)
                .mapToLong(LedgerEntry::getAmountPaise).sum();
        long credits = savedLedger.stream().filter(e -> e.getDirection() == LedgerDirection.CREDIT)
                .mapToLong(LedgerEntry::getAmountPaise).sum();
        assertThat(debits).isEqualTo(credits).isEqualTo(102_000);
        assertThat(savedLedger).filteredOn(e -> e.getAccount() == LedgerAccount.PROVIDER_PAYABLE)
                .singleElement().satisfies(e -> assertThat(e.getAmountPaise()).isEqualTo(98_000));
    }

    @Test
    void outcomeDefaultsToSuccess() {
        order();
        TransactionView t = service.simulate(BUYER, payOrder(PaymentMethod.WALLET, null, 102_000), null).transaction();
        assertThat(t.status()).isEqualTo("SUCCESS");
        assertThat(t.method()).isEqualTo("WALLET");
    }

    /* ─────────────────────────── failed / cancelled ─────────────────────────── */

    @Test
    void failedPaymentIsRecordedButCreatesNoCommissionPayoutOrLedger() {
        PurchaseOrder o = order();

        TransactionView t = service.simulate(BUYER, payOrder(PaymentMethod.CARD, SimulatedOutcome.FAILED, 102_000), null)
                .transaction();

        assertThat(t.status()).isEqualTo("FAILED");
        assertThat(t.failureReason()).contains("declined");
        assertThat(t.currentProviderNetPaise()).isZero();
        assertThat(t.currentPlatformRevenuePaise()).isZero();
        assertThat(t.payoutReference()).isNull();
        assertThat(savedPayments).singleElement().satisfies(p -> {
            assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(p.getSettlementKey()).isNull();
        });
        assertThat(savedCommissions).isEmpty();
        assertThat(savedPayouts).isEmpty();
        assertThat(savedLedger).isEmpty();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(o.getFailureReason()).isEqualTo(t.failureReason());
        verify(productService, never()).confirmSale(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void cancelledPaymentIsRecordedButChargesNothing() {
        PurchaseOrder o = order();

        TransactionView t = service.simulate(BUYER, payOrder(PaymentMethod.NET_BANKING, SimulatedOutcome.CANCELLED, 102_000),
                null).transaction();

        assertThat(t.status()).isEqualTo("CANCELLED");
        assertThat(t.failureReason()).contains("cancelled");
        assertThat(savedPayouts).isEmpty();
        assertThat(savedCommissions).isEmpty();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verify(productService, never()).confirmSale(anyLong(), anyLong(), anyString(), anyString());
    }

    /* ─────────────────────────── incorrect amount ─────────────────────────── */

    @Test
    void incorrectAmountIsRejectedBeforeAnythingIsRecorded() {
        order();

        assertThatThrownBy(() -> service.simulate(BUYER, payOrder(PaymentMethod.UPI, SimulatedOutcome.SUCCESS, 100_000), null))
                .isInstanceOf(UnprocessableException.class)
                .hasMessageContaining("Incorrect amount")
                .satisfies(ex -> {
                    UnprocessableException u = (UnprocessableException) ex;
                    assertThat(u.getDetails()).containsEntry("expectedAmountPaise", 102_000L)
                            .containsEntry("receivedAmountPaise", 100_000L);
                });
        // even a simulated failure must send the right amount
        assertThatThrownBy(() -> service.simulate(BUYER, payOrder(PaymentMethod.UPI, SimulatedOutcome.FAILED, 1), null))
                .isInstanceOf(UnprocessableException.class);
        assertThat(savedPayments).isEmpty();
    }

    /* ─────────────────────────── duplicates ─────────────────────────── */

    @Test
    void duplicatePaymentOfAPaidOrderIsRejected() {
        order();
        PaymentTransaction existing = new PaymentTransaction();
        existing.setTransactionId("TXN-20261125-EXISTING01");
        when(payments.findFirstByPurchaseOrderIdAndStatusIn(eq(11L), any())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.simulate(BUYER, payOrder(PaymentMethod.UPI, SimulatedOutcome.SUCCESS, 102_000), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already paid")
                .satisfies(ex -> assertThat(((ConflictException) ex).getDetails())
                        .containsEntry("transactionId", "TXN-20261125-EXISTING01"));
        assertThat(savedPayments).isEmpty();
        verify(productService, never()).confirmSale(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void repeatedIdempotencyKeyReturnsTheOriginalAttempt() {
        order();
        service.simulate(BUYER, payOrder(PaymentMethod.UPI, SimulatedOutcome.SUCCESS, 102_000), "key-1");
        PaymentTransaction first = savedPayments.get(0);
        when(payments.findByBuyerAccountIdAndIdempotencyKey(501L, "key-1")).thenReturn(Optional.of(first));

        PaymentService.Result again = service.simulate(BUYER,
                payOrder(PaymentMethod.UPI, SimulatedOutcome.SUCCESS, 102_000), "  key-1 ");
        assertThat(again.replayed()).isTrue();
        assertThat(again.transaction().transactionId()).isEqualTo(first.getTransactionId());
        assertThat(savedPayments).hasSize(1);
        assertThat(first.getIdempotencyKey()).isEqualTo("key-1");
    }

    @Test
    void cancelledOrExpiredOrdersCannotBePaid() {
        PurchaseOrder o = order();
        o.setStatus(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> service.simulate(BUYER, payOrder(PaymentMethod.UPI, null, 102_000), null))
                .isInstanceOf(ConflictException.class).hasMessageContaining("cancelled");

        o.setStatus(OrderStatus.PENDING_PAYMENT);
        o.setExpiresAt(LocalDateTime.now(CLOCK).minusSeconds(1));
        assertThatThrownBy(() -> service.simulate(BUYER, payOrder(PaymentMethod.UPI, null, 102_000), null))
                .isInstanceOf(ConflictException.class).hasMessageContaining("payment window");
        assertThat(savedPayments).isEmpty();
    }

    @Test
    void someoneElsesOrderIsNotFound() {
        order();
        AuthenticatedAccount stranger = new AuthenticatedAccount(999L, "x@reloop.test", "CUSTOMER");
        assertThatThrownBy(() -> service.simulate(stranger, payOrder(PaymentMethod.UPI, null, 102_000), null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void exactlyOneTargetIsRequired() {
        assertThatThrownBy(() -> service.simulate(BUYER,
                new SimulatePaymentRequest(null, null, PaymentMethod.UPI, null, 100L), null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.simulate(BUYER,
                new SimulatePaymentRequest(1L, 2L, PaymentMethod.UPI, null, 100L), null))
                .isInstanceOf(BadRequestException.class);
    }

    /* ─────────────────────────── repair booking ─────────────────────────── */

    @Test
    void repairPaymentCopiesTheQuotesFrozenRatesAndPaysTheRepairerAccount() {
        RepairQuote q = new RepairQuote();
        ReflectionTestUtils.setField(q, "id", 31L);
        q.setRepairRequestExternalId(2001L);
        q.setRepairerProfileId(77L);
        q.setRepairerAccountId(601L);
        q.setCustomerAccountId(BUYER.accountId());
        q.setQuoteRevision(1);
        q.setStatus(QuoteStatus.APPROVED);
        // frozen when the quote was sent at 3% + 1%; today's configuration is irrelevant
        FeeCalculator.Breakdown b = FeeCalculator.compute(150_000, new BigDecimal("0.03"), new BigDecimal("0.01"));
        q.setBaseAmountPaise(b.baseAmountPaise());
        q.setCustomerFeeRate(b.customerFeeRate());
        q.setCustomerFeePaise(b.customerFeePaise());
        q.setTotalPayablePaise(b.totalPayablePaise());
        q.setRepairerCommissionRate(b.repairerCommissionRate());
        q.setRepairerCommissionPaise(b.repairerCommissionPaise());
        q.setRepairerNetPaise(b.repairerNetPaise());
        when(quotes.findByIdForUpdate(31L)).thenReturn(Optional.of(q));
        when(quotes.findById(31L)).thenReturn(Optional.of(q));

        TransactionView t = service.simulate(BUYER,
                new SimulatePaymentRequest(null, 31L, PaymentMethod.UPI, SimulatedOutcome.SUCCESS, 154_500L), null)
                .transaction();

        assertThat(t.transactionType()).isEqualTo(TransactionType.REPAIR_BOOKING.name());
        assertThat(t.customerFeeRate()).isEqualByComparingTo("0.03");
        assertThat(t.customerFeePaise()).isEqualTo(4_500);
        assertThat(t.totalChargedPaise()).isEqualTo(154_500);
        assertThat(t.providerCommissionRate()).isEqualByComparingTo("0.01");
        assertThat(t.providerCommissionPaise()).isEqualTo(1_500);
        assertThat(t.providerNetPaise()).isEqualTo(148_500);
        assertThat(t.platformRevenuePaise()).isEqualTo(6_000);
        assertThat(t.providerAccountId()).isEqualTo(601L);
        assertThat(t.repairRequestId()).isEqualTo(2001L);
        assertThat(savedPayouts).singleElement().satisfies(po -> {
            assertThat(po.getProviderType()).isEqualTo(ProviderType.REPAIRER);
            assertThat(po.getProviderAccountId()).isEqualTo(601L);
            assertThat(po.getProviderId()).isEqualTo(77L);
        });
        verify(repairService).confirmPayment(2001L, new PaymentConfirmation(31L, savedPayments.get(0).getId(),
                t.transactionId(), 154_500L));
    }

    @Test
    void unapprovedQuoteCannotBePaid() {
        RepairQuote q = new RepairQuote();
        ReflectionTestUtils.setField(q, "id", 32L);
        q.setCustomerAccountId(BUYER.accountId());
        q.setQuoteRevision(2);
        q.setStatus(QuoteStatus.SENT);
        q.setTotalPayablePaise(10_200L);
        when(quotes.findByIdForUpdate(32L)).thenReturn(Optional.of(q));

        assertThatThrownBy(() -> service.simulate(BUYER,
                new SimulatePaymentRequest(null, 32L, PaymentMethod.UPI, null, 10_200L), null))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).getDetails()).containsEntry("quoteStatus", "SENT"));
        verify(repairService, never()).confirmPayment(anyLong(), any());
    }
}
