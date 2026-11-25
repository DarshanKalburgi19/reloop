package com.reloop.orderpayment.dto.quote;

import com.reloop.orderpayment.entity.RepairQuote;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A repair quote with its frozen fee breakdown. Field names are the REST contract read by repair-service
 * ({@code QuoteView} there); extra fields are ignored by it.
 */
public record RepairQuoteView(
        Long id,
        Long repairRequestId,
        Long repairerProfileId,
        Long customerAccountId,
        int revision,
        String status,
        long baseAmountPaise,
        BigDecimal customerFeeRate,
        long customerFeePaise,
        long totalPayablePaise,
        BigDecimal repairerCommissionRate,
        long repairerCommissionPaise,
        long repairerNetPaise,
        String currency,
        String summary,
        LocalDateTime sentAt,
        LocalDateTime validUntil,
        LocalDateTime approvedAt,
        LocalDateTime rejectedAt) {

    public static RepairQuoteView of(RepairQuote q) {
        return new RepairQuoteView(q.getId(), q.getRepairRequestExternalId(), q.getRepairerProfileId(),
                q.getCustomerAccountId(), q.getQuoteRevision(), q.getStatus().name(), q.getBaseAmountPaise(),
                q.getCustomerFeeRate(), q.getCustomerFeePaise(), q.getTotalPayablePaise(), q.getRepairerCommissionRate(),
                q.getRepairerCommissionPaise(), q.getRepairerNetPaise(), q.getCurrency(), q.getSummary(), q.getSentAt(),
                q.getValidUntil(), q.getApprovedAt(), q.getRejectedAt());
    }
}
