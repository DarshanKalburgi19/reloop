package com.reloop.orderpayment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Refund calculation: proportional split of a refund across customer fee, provider commission and provider net. */
class RefundCalculatorTest {

    // ₹1,000 base, ₹20 customer fee, ₹20 provider commission (2% + 2%)
    private static final long BASE = 100_000;
    private static final long FEE = 2_000;
    private static final long COMMISSION = 2_000;

    @Test
    void fullRefundReturnsEverythingTheCustomerPaidAndReversesAllRevenue() {
        RefundCalculator.Split s = RefundCalculator.split(BASE, FEE, COMMISSION, 0, 0, 0, null);

        assertThat(s.refundedBasePaise()).isEqualTo(100_000);
        assertThat(s.refundedCustomerFeePaise()).isEqualTo(2_000);
        assertThat(s.customerRefundPaise()).isEqualTo(102_000);          // the whole ₹1,020
        assertThat(s.reversedProviderCommissionPaise()).isEqualTo(2_000);
        assertThat(s.providerNetReversedPaise()).isEqualTo(98_000);      // the provider's ₹980 is taken back
        assertThat(s.platformRevenueReversedPaise()).isEqualTo(4_000);   // ReLoop's ₹40 is reversed
        assertThat(s.fullyRefunded()).isTrue();
    }

    @Test
    void partialRefundIsProportional() {
        RefundCalculator.Split s = RefundCalculator.split(BASE, FEE, COMMISSION, 0, 0, 0, 25_000L);

        assertThat(s.customerRefundPaise()).isEqualTo(25_500);           // ₹250 + ₹5 fee
        assertThat(s.providerNetReversedPaise()).isEqualTo(24_500);      // ₹250 - ₹5 commission
        assertThat(s.platformRevenueReversedPaise()).isEqualTo(1_000);   // ₹5 + ₹5
        assertThat(s.fullyRefunded()).isFalse();
        // money is conserved: customer refund = provider reversal + ReLoop reversal
        assertThat(s.customerRefundPaise()).isEqualTo(s.providerNetReversedPaise() + s.platformRevenueReversedPaise());
    }

    @Test
    void lastRefundTakesExactlyWhatIsLeftSoRoundingNeverLeavesPaise() {
        // ₹333.33 + 2% = 666.67 → fee 667 paise, commission 667 paise
        long base = 33_333;
        long fee = FeeCalculator.percentOf(base, new java.math.BigDecimal("0.02"));
        long commission = fee;
        RefundCalculator.Split first = RefundCalculator.split(base, fee, commission, 0, 0, 0, 11_111L);
        RefundCalculator.Split second = RefundCalculator.split(base, fee, commission, 11_111,
                first.refundedCustomerFeePaise(), first.reversedProviderCommissionPaise(), 11_111L);
        RefundCalculator.Split last = RefundCalculator.split(base, fee, commission, 22_222,
                first.refundedCustomerFeePaise() + second.refundedCustomerFeePaise(),
                first.reversedProviderCommissionPaise() + second.reversedProviderCommissionPaise(), null);

        assertThat(last.refundedBasePaise()).isEqualTo(11_111);
        assertThat(first.refundedCustomerFeePaise() + second.refundedCustomerFeePaise() + last.refundedCustomerFeePaise())
                .isEqualTo(fee);
        assertThat(first.reversedProviderCommissionPaise() + second.reversedProviderCommissionPaise()
                + last.reversedProviderCommissionPaise()).isEqualTo(commission);
        assertThat(first.customerRefundPaise() + second.customerRefundPaise() + last.customerRefundPaise())
                .isEqualTo(base + fee);
        assertThat(last.fullyRefunded()).isTrue();
    }

    @Test
    void refundingTheRemainderAfterAPartialRefund() {
        RefundCalculator.Split rest = RefundCalculator.split(BASE, FEE, COMMISSION, 25_000, 500, 500, null);
        assertThat(rest.customerRefundPaise()).isEqualTo(76_500);        // ₹750 + ₹15
        assertThat(rest.providerNetReversedPaise()).isEqualTo(73_500);   // ₹750 - ₹15
        assertThat(rest.platformRevenueReversedPaise()).isEqualTo(3_000);
        assertThat(rest.fullyRefunded()).isTrue();
    }

    @Test
    void invalidRefundAmountsAreRejected() {
        assertThatThrownBy(() -> RefundCalculator.split(BASE, FEE, COMMISSION, 0, 0, 0, 100_001L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exceeds");
        assertThatThrownBy(() -> RefundCalculator.split(BASE, FEE, COMMISSION, 0, 0, 0, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefundCalculator.split(BASE, FEE, COMMISSION, 0, 0, 0, -5L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefundCalculator.split(BASE, FEE, COMMISSION, BASE, FEE, COMMISSION, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fully refunded");
    }

    @Test
    void zeroFeeConfigurationRefundsOnlyTheBase() {
        RefundCalculator.Split s = RefundCalculator.split(50_000, 0, 0, 0, 0, 0, 10_000L);
        assertThat(s.customerRefundPaise()).isEqualTo(10_000);
        assertThat(s.providerNetReversedPaise()).isEqualTo(10_000);
        assertThat(s.platformRevenueReversedPaise()).isZero();
    }
}
