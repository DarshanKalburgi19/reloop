package com.reloop.orderpayment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Splits a (partial) refund of a successful payment. Pure arithmetic in paise, no I/O.
 *
 * <p>A refund is requested as an amount of the BASE price (item / repair price). The customer fee and the provider
 * commission are reversed in proportion, so a full refund returns everything the customer paid and takes back
 * exactly the provider's net and ReLoop's revenue:</p>
 * <pre>
 * refundedCustomerFee        = round(customerFee × refundBase / base)          (HALF_UP)
 * reversedProviderCommission = round(providerCommission × refundBase / base)   (HALF_UP)
 * customerRefund             = refundBase + refundedCustomerFee
 * providerNetReversed        = refundBase − reversedProviderCommission
 * platformRevenueReversed    = refundedCustomerFee + reversedProviderCommission
 * </pre>
 * The refund that brings the refunded base to the full base takes the REMAINING fee and commission instead of
 * rounding, so the sum of all refunds never drifts by a paisa from the original payment.
 *
 * <p>Example: base ₹1,000, fee ₹20, commission ₹20. Refunding ₹250 of the base returns ₹255 to the customer,
 * takes ₹245 back from the seller and reverses ₹10 of ReLoop revenue. Refunding the remaining ₹750 returns ₹765,
 * takes back ₹735 and reverses ₹30, i.e. ₹1,020 / ₹980 / ₹40 in total.</p>
 */
public final class RefundCalculator {

    private RefundCalculator() {
    }

    /**
     * @param baseAmountPaise                 payment base (provider gross)
     * @param customerFeePaise                customer fee charged on the payment
     * @param providerCommissionPaise         provider commission on the payment
     * @param alreadyRefundedBasePaise        base already refunded by earlier refunds
     * @param alreadyRefundedFeePaise         customer fee already refunded
     * @param alreadyReversedCommissionPaise  provider commission already reversed
     * @param refundBasePaise                 base to refund now, or {@code null} for everything that remains
     */
    public static Split split(long baseAmountPaise, long customerFeePaise, long providerCommissionPaise,
                              long alreadyRefundedBasePaise, long alreadyRefundedFeePaise,
                              long alreadyReversedCommissionPaise, Long refundBasePaise) {
        long remainingBase = baseAmountPaise - alreadyRefundedBasePaise;
        if (remainingBase <= 0) {
            throw new IllegalArgumentException("The payment has already been fully refunded");
        }
        long refundBase = refundBasePaise == null ? remainingBase : refundBasePaise;
        if (refundBase <= 0) {
            throw new IllegalArgumentException("Refund amount must be positive");
        }
        if (refundBase > remainingBase) {
            throw new IllegalArgumentException("Refund amount exceeds the refundable base amount of " + remainingBase + " paise");
        }
        long fee;
        long commission;
        boolean finalRefund = refundBase == remainingBase;
        if (finalRefund) {
            fee = customerFeePaise - alreadyRefundedFeePaise;
            commission = providerCommissionPaise - alreadyReversedCommissionPaise;
        } else {
            fee = proportion(customerFeePaise, refundBase, baseAmountPaise);
            commission = proportion(providerCommissionPaise, refundBase, baseAmountPaise);
            // never exceed what is left (possible only through rounding on many tiny refunds)
            fee = Math.min(fee, customerFeePaise - alreadyRefundedFeePaise);
            commission = Math.min(commission, providerCommissionPaise - alreadyReversedCommissionPaise);
        }
        return new Split(refundBase, fee, commission, refundBase + fee, refundBase - commission, fee + commission,
                finalRefund);
    }

    private static long proportion(long amount, long part, long whole) {
        return BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(part))
                .divide(BigDecimal.valueOf(whole), 0, RoundingMode.HALF_UP).longValueExact();
    }

    /**
     * @param refundedBasePaise                part of the base price returned
     * @param refundedCustomerFeePaise         part of the customer fee returned
     * @param reversedProviderCommissionPaise  part of the provider commission given back to the provider
     * @param customerRefundPaise              total returned to the customer
     * @param providerNetReversedPaise         taken back from the provider's earnings
     * @param platformRevenueReversedPaise     ReLoop revenue reversed
     * @param fullyRefunded                    true when nothing refundable remains afterwards
     */
    public record Split(
            long refundedBasePaise,
            long refundedCustomerFeePaise,
            long reversedProviderCommissionPaise,
            long customerRefundPaise,
            long providerNetReversedPaise,
            long platformRevenueReversedPaise,
            boolean fullyRefunded) {
    }
}
