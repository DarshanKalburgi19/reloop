package com.reloop.orderpayment.service;

import com.reloop.orderpayment.config.FeeProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Fee arithmetic in integer paise. Each fee is {@code round(base × rate)} with HALF_UP to the nearest paisa.
 *
 * <pre>
 * base ₹1,500.00 (150000 paise), rates 2% / 2%
 *   customer fee      =   3000   → customer pays 153000 (₹1,530.00)
 *   repairer commission =  3000  → repairer receives 147000 (₹1,470.00)
 *   platform keeps    =   6000   (fee + commission)
 * </pre>
 */
@Component
public class FeeCalculator {

    private final FeeProperties fees;

    public FeeCalculator(FeeProperties fees) {
        this.fees = fees;
    }

    /** Breakdown with the CURRENT configured rates (used when a quote is issued). */
    public Breakdown current(long baseAmountPaise) {
        return compute(baseAmountPaise, fees.customerFeeRate(), fees.repairerCommissionRate());
    }

    public static Breakdown compute(long baseAmountPaise, BigDecimal customerFeeRate, BigDecimal repairerCommissionRate) {
        if (baseAmountPaise <= 0) {
            throw new IllegalArgumentException("base amount must be positive");
        }
        long fee = percentOf(baseAmountPaise, customerFeeRate);
        long commission = percentOf(baseAmountPaise, repairerCommissionRate);
        return new Breakdown(baseAmountPaise, customerFeeRate, fee, Math.addExact(baseAmountPaise, fee),
                repairerCommissionRate, commission, baseAmountPaise - commission);
    }

    private static long percentOf(long paise, BigDecimal rate) {
        return BigDecimal.valueOf(paise).multiply(rate).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public record Breakdown(
            long baseAmountPaise,
            BigDecimal customerFeeRate,
            long customerFeePaise,
            long totalPayablePaise,
            BigDecimal repairerCommissionRate,
            long repairerCommissionPaise,
            long repairerNetPaise) {
    }
}
