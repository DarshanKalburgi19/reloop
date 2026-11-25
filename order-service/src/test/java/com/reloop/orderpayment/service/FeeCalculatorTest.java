package com.reloop.orderpayment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reloop.orderpayment.config.FeeProperties;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FeeCalculatorTest {

    private static final BigDecimal TWO_PERCENT = new BigDecimal("0.0200");

    @Test
    void twoPercentEachSideOnFifteenHundredRupees() {
        FeeCalculator.Breakdown b = new FeeCalculator(new FeeProperties(TWO_PERCENT, TWO_PERCENT)).current(150_000);

        assertThat(b.customerFeePaise()).isEqualTo(3_000);
        assertThat(b.totalPayablePaise()).isEqualTo(153_000);
        assertThat(b.repairerCommissionPaise()).isEqualTo(3_000);
        assertThat(b.repairerNetPaise()).isEqualTo(147_000);
        assertThat(b.customerFeeRate()).isEqualByComparingTo("0.02");
    }

    @Test
    void roundsHalfUpToTheNearestPaisa() {
        // 125 × 0.02 = 2.5 → 3 ; 124 × 0.02 = 2.48 → 2
        assertThat(FeeCalculator.compute(125, TWO_PERCENT, TWO_PERCENT).customerFeePaise()).isEqualTo(3);
        assertThat(FeeCalculator.compute(124, TWO_PERCENT, TWO_PERCENT).customerFeePaise()).isEqualTo(2);
    }

    @Test
    void ratesComeFromConfigurationNotCode() {
        FeeCalculator.Breakdown b = new FeeCalculator(
                new FeeProperties(new BigDecimal("0.0500"), new BigDecimal("0.1000"))).current(100_000);

        assertThat(b.customerFeePaise()).isEqualTo(5_000);
        assertThat(b.totalPayablePaise()).isEqualTo(105_000);
        assertThat(b.repairerCommissionPaise()).isEqualTo(10_000);
        assertThat(b.repairerNetPaise()).isEqualTo(90_000);
    }

    @Test
    void zeroRatesMeanNoFees() {
        FeeCalculator.Breakdown b = FeeCalculator.compute(99_999, BigDecimal.ZERO, BigDecimal.ZERO);
        assertThat(b.totalPayablePaise()).isEqualTo(99_999);
        assertThat(b.repairerNetPaise()).isEqualTo(99_999);
    }

    @Test
    void rejectsNonPositiveBase() {
        assertThatThrownBy(() -> FeeCalculator.compute(0, TWO_PERCENT, TWO_PERCENT))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
