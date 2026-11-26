package com.reloop.orderpayment.dto.payment;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.reloop.orderpayment.dto.refund.RefundView;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ONE simulated payment as every dashboard sees it (customer, seller and repairer get the same numbers).
 *
 * <pre>
 * totalChargedPaise    = baseAmountPaise + customerFeePaise                 (what the customer paid)
 * providerNetPaise     = baseAmountPaise − providerCommissionPaise          (what the provider earns)
 * platformRevenuePaise = customerFeePaise + providerCommissionPaise         (ReLoop revenue)
 * </pre>
 * Refund totals show how much of each part has been reversed; {@code current*} fields are after refunds.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionView(
        Long id,
        String transactionId,
        String transactionType,
        String status,
        String method,
        boolean simulated,
        Long purchaseOrderId,
        String orderNumber,
        Long productId,
        Long repairQuoteId,
        Long repairRequestId,
        Long buyerAccountId,
        String providerType,
        Long providerId,
        Long providerAccountId,
        long baseAmountPaise,
        BigDecimal customerFeeRate,
        long customerFeePaise,
        long totalChargedPaise,
        BigDecimal providerCommissionRate,
        long providerCommissionPaise,
        long providerNetPaise,
        long platformRevenuePaise,
        long refundedAmountPaise,
        long reversedProviderNetPaise,
        long reversedPlatformRevenuePaise,
        long currentProviderNetPaise,
        long currentPlatformRevenuePaise,
        String currency,
        String failureReason,
        LocalDateTime attemptedAt,
        LocalDateTime completedAt,
        String commissionTransactionId,
        String commissionStatus,
        String payoutReference,
        String payoutStatus,
        List<RefundView> refunds,
        String receiptUrl,
        String note) {

    public static final String SIMULATION_NOTE =
            "Simulated payment: no money moved, no payment gateway was contacted, no card or bank details were stored.";
}
