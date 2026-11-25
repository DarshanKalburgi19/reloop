package com.reloop.orderpayment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Commission calculation, customer fee and provider net earnings for the business example and edge cases. */
class CommissionCalculationTest {

    private static final BigDecimal TWO = new BigDecimal("0.02");

    @Test
    void theBusinessExample() {
        // ₹1,000 → fee ₹20 → customer pays ₹1,020 → commission ₹20 → provider net ₹980 → ReLoop ₹40
        FeeCalculator.Breakdown b = FeeCalculator.compute(100_000, TWO, TWO);
        assertThat(b.baseAmountPaise()).isEqualTo(100_000);
        assertThat(b.customerFeePaise()).isEqualTo(2_000);
        assertThat(b.totalPayablePaise()).isEqualTo(102_000);
        assertThat(b.providerCommissionPaise()).isEqualTo(2_000);
        assertThat(b.providerNetPaise()).isEqualTo(98_000);
        assertThat(b.platformRevenuePaise()).isEqualTo(4_000);
    }

    @Test
    void customerFeeIsAddedOnTopAndNeverTakenFromTheProvider() {
        FeeCalculator.Breakdown b = FeeCalculator.compute(250_000, new BigDecimal("0.05"), BigDecimal.ZERO);
        assertThat(b.customerFeePaise()).isEqualTo(12_500);
        assertThat(b.totalPayablePaise()).isEqualTo(262_500);
        assertThat(b.providerNetPaise()).isEqualTo(250_000);   // provider keeps the full price
        assertThat(b.platformRevenuePaise()).isEqualTo(12_500);
    }

    @Test
    void providerNetIsGrossMinusCommissionAndIndependentOfTheCustomerFee() {
        FeeCalculator.Breakdown b = FeeCalculator.compute(250_000, BigDecimal.ZERO, new BigDecimal("0.10"));
        assertThat(b.totalPayablePaise()).isEqualTo(250_000);   // customer pays only the price
        assertThat(b.providerCommissionPaise()).isEqualTo(25_000);
        assertThat(b.providerNetPaise()).isEqualTo(225_000);
        assertThat(b.platformRevenuePaise()).isEqualTo(25_000);
    }

    @ParameterizedTest(name = "₹{0} paise at 2% + 2%")
    @CsvSource({
        // base,   fee,  total,  commission, net,    revenue
        "150000,  3000, 153000, 3000,       147000, 6000",
        "1,       0,    1,      0,          1,      0",
        "25,      1,    26,     1,          24,     2",      // 0.5 paise rounds half-up
        "99999,   2000, 101999, 2000,       97999,  4000",
        "1234567, 24691, 1259258, 24691,    1209876, 49382"
    })
    void roundingToWholePaise(long base, long fee, long total, long commission, long net, long revenue) {
        FeeCalculator.Breakdown b = FeeCalculator.compute(base, TWO, TWO);
        assertThat(b.customerFeePaise()).isEqualTo(fee);
        assertThat(b.totalPayablePaise()).isEqualTo(total);
        assertThat(b.providerCommissionPaise()).isEqualTo(commission);
        assertThat(b.providerNetPaise()).isEqualTo(net);
        assertThat(b.platformRevenuePaise()).isEqualTo(revenue);
        // invariants: what the customer pays = what the provider nets + what ReLoop keeps
        assertThat(b.totalPayablePaise()).isEqualTo(b.providerNetPaise() + b.platformRevenuePaise());
    }

    @Test
    void ratesMustBeBetweenZeroAndOne() {
        assertThatThrownBy(() -> FeeCalculator.compute(1_000, new BigDecimal("-0.01"), TWO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FeeCalculator.compute(1_000, TWO, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FeeCalculator.compute(1_000, null, TWO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void moneyFormattingForReceipts() {
        assertThat(Money.format(102_000)).isEqualTo("₹1,020.00");
        assertThat(Money.format(98_050)).isEqualTo("₹980.50");
        assertThat(Money.format(-500)).isEqualTo("-₹5.00");
        assertThat(Money.percent(new BigDecimal("0.0200"))).isEqualTo("2%");
        assertThat(Money.percent(new BigDecimal("0.025"))).isEqualTo("2.5%");
    }
}
