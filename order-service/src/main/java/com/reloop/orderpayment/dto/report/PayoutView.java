package com.reloop.orderpayment.dto.report;

import com.reloop.orderpayment.entity.ProviderPayout;
import java.time.LocalDateTime;

/**
 * A simulated provider payout (one per successful payment).
 * {@code netAmountPaise = grossAmountPaise − commissionPaise}; {@code currentNetPaise = netAmountPaise − reversedNetPaise}.
 */
public record PayoutView(
        String payoutReference,
        String transactionId,
        String transactionType,
        String providerType,
        Long providerId,
        Long providerAccountId,
        long grossAmountPaise,
        long commissionPaise,
        long netAmountPaise,
        long refundedGrossPaise,
        long reversedCommissionPaise,
        long reversedNetPaise,
        long currentNetPaise,
        String status,
        String currency,
        LocalDateTime createdAt,
        LocalDateTime paidAt) {

    public static PayoutView of(ProviderPayout p, String transactionId) {
        return new PayoutView(p.getPayoutReference(), transactionId,
                p.getTransactionType() == null ? null : p.getTransactionType().name(), p.getProviderType().name(),
                p.getProviderId(), p.getProviderAccountId(), p.getGrossAmountPaise(), p.getCommissionPaise(),
                p.getNetAmountPaise(), p.getRefundedGrossPaise(), p.getReversedCommissionPaise(), p.getReversedNetPaise(),
                p.currentNetPaise(), p.getStatus().name(), p.getCurrency(), p.getCreatedAt(), p.getPaidAt());
    }
}
