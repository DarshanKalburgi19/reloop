package com.reloop.repair.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void rupeesAndPaise() {
        assertThat(Money.toPaise(new BigDecimal("1499"))).isEqualTo(149_900L);
        assertThat(Money.toPaise(new BigDecimal("899.5"))).isEqualTo(89_950L);
        assertThat(Money.toRupees(89_950L)).isEqualByComparingTo("899.50");
    }

    @Test
    void searchBoundsRoundInwards() {
        assertThat(Money.ceilPaise(new BigDecimal("10.001"))).isEqualTo(1_001L);
        assertThat(Money.floorPaise(new BigDecimal("10.009"))).isEqualTo(1_000L);
        assertThat(Money.ceilPaise(new BigDecimal("500"))).isEqualTo(50_000L);
    }

    @Test
    void formatsIndianRupees() {
        assertThat(Money.format(149_900L)).isEqualTo("₹1,499");
        assertThat(Money.format(19_950L)).isEqualTo("₹199.50");
        assertThat(Money.format(8_000L)).isEqualTo("₹80");
    }
}
