package com.reloop.orderpayment.service;

import com.reloop.orderpayment.config.FeeProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Fee arithmetic in integer paise. Each fee is {@code round(base × rate)} with HALF_UP to the nearest paisa.
 *
 * <pre>
 * base ₹1,000.00 (100000 paise), rates 2% / 2%
 *   customer fee         =   2000  → customer pays    102000 (₹1,020.00)
 *   provider commission  =   2000  → provider earns    98000 (₹980.00)
 *   ReLoop revenue       =   4000  (customer fee + provider commission, ₹40.00)
 * </pre>
 * The same formula applies to marketplace purchases (provider = seller) and repair bookings (provider = repairer).
 */
@Component
public class FeeCalculator {

    private final FeeProperties fees;

    public FeeCalculator(FeeProperties fees) {
        this.fees = fees;
    }

    /** Breakdown with the configured DEFAULT rates (application.properties). */
    public Breakdown current(long baseAmountPaise) {
        return compute(baseAmountPaise, fees.customerFeeRate(), fees.providerCommissionRate());
    }

    public static Breakdown compute(long baseAmountPaise, BigDecimal customerFeeRate, BigDecimal providerCommissionRate) {
        if (baseAmountPaise <= 0) {
            throw new IllegalArgumentException("base amount must be positive");
        }
        requireRate(customerFeeRate, "customer fee rate");
        requireRate(providerCommissionRate, "provider commission rate");
        long fee = percentOf(baseAmountPaise, customerFeeRate);
        long commission = percentOf(baseAmountPaise, providerCommissionRate);
        return new Breakdown(baseAmountPaise, customerFeeRate, fee, Math.addExact(baseAmountPaise, fee),
                providerCommissionRate, commission, baseAmountPaise - commission, Math.addExact(fee, commission));
    }

    /** {@code round(paise × rate)}, HALF_UP. */
    public static long percentOf(long paise, BigDecimal rate) {
        return BigDecimal.valueOf(paise).multiply(rate).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static void requireRate(BigDecimal rate, String name) {
        if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
    }

    /**
     * @param baseAmountPaise          provider's gross amount (item / repair price)
     * @param customerFeePaise         added to what the customer pays
     * @param totalPayablePaise        base + customer fee
     * @param providerCommissionPaise  deducted from the provider's gross
     * @param providerNetPaise         base − provider commission
     * @param platformRevenuePaise     customer fee + provider commission (ReLoop revenue)
     */
    public record Breakdown(
            long baseAmountPaise,
            BigDecimal customerFeeRate,
            long customerFeePaise,
            long totalPayablePaise,
            BigDecimal providerCommissionRate,
            long providerCommissionPaise,
            long providerNetPaise,
            long platformRevenuePaise) {

        /** Repair wording of {@link #providerCommissionRate()}. */
        public BigDecimal repairerCommissionRate() {
            return providerCommissionRate;
        }

        /** Repair wording of {@link #providerCommissionPaise()}. */
        public long repairerCommissionPaise() {
            return providerCommissionPaise;
        }

        /** Repair wording of {@link #providerNetPaise()}. */
        public long repairerNetPaise() {
            return providerNetPaise;
        }
    }
}
