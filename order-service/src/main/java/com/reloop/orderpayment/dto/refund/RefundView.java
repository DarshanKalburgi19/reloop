package com.reloop.orderpayment.dto.refund;

import com.reloop.orderpayment.entity.RefundRecord;
import java.time.LocalDateTime;

/**
 * A simulated refund. {@code refundAmountPaise = refundedBasePaise + refundedCustomerFeePaise} goes back to the
 * customer; {@code providerNetReversedPaise} is taken from the provider's earnings; {@code reversedCommissionPaise}
 * (fee + commission) is ReLoop revenue given back.
 */
public record RefundView(
        String refundReference,
        String transactionId,
        String status,
        String reason,
        String note,
        long refundAmountPaise,
        long refundedBasePaise,
        long refundedCustomerFeePaise,
        long reversedProviderCommissionPaise,
        long providerNetReversedPaise,
        long reversedCommissionPaise,
        String currency,
        LocalDateTime completedAt) {

    public static RefundView of(RefundRecord r, String transactionId) {
        return new RefundView(r.getRefundReference(), transactionId, r.getStatus().name(), r.getReason().name(),
                r.getNote(), r.getRefundAmountPaise(), r.getRefundedBasePaise(), r.getRefundedCustomerFeePaise(),
                r.getReversedProviderCommissionPaise(), r.getProviderNetReversedPaise(), r.getReversedCommissionPaise(),
                r.getCurrency(), r.getCompletedAt());
    }
}
