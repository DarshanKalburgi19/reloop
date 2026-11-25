package com.reloop.orderpayment.dto.payment;

import com.reloop.orderpayment.entity.CommissionRecord;
import com.reloop.orderpayment.entity.PaymentTransaction;
import com.reloop.orderpayment.entity.ProviderPayout;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A (simulated) payment with its frozen fee breakdown and, on success, the separate commission and payout
 * records it produced.
 */
public record PaymentView(
        Long id,
        String transactionId,
        Long repairQuoteId,
        Long repairRequestId,
        String status,
        String method,
        boolean simulated,
        long baseAmountPaise,
        BigDecimal customerFeeRate,
        long customerFeePaise,
        long totalChargedPaise,
        BigDecimal repairerCommissionRate,
        long repairerCommissionPaise,
        long providerNetPaise,
        String currency,
        String failureReason,
        LocalDateTime attemptedAt,
        LocalDateTime completedAt,
        String commissionTransactionId,
        String payoutReference) {

    public static PaymentView of(PaymentTransaction p, Long repairRequestId, CommissionRecord commission, ProviderPayout payout) {
        return new PaymentView(p.getId(), p.getTransactionId(), p.getRepairQuoteId(), repairRequestId,
                p.getStatus().name(), p.getMethod().name(), true, p.getBaseAmountPaise(), p.getCustomerFeeRate(),
                p.getCustomerFeePaise(), p.getTotalChargedPaise(), p.getRepairerCommissionRate(),
                p.getRepairerCommissionPaise(), p.getProviderNetPaise(), p.getCurrency(), p.getFailureReason(),
                p.getAttemptedAt(), p.getCompletedAt(),
                commission == null ? null : commission.getTransactionId(),
                payout == null ? null : payout.getPayoutReference());
    }
}
