package com.reloop.repair.client;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** order-service's quote as returned by its internal quote API (unknown fields are ignored). */
public record QuoteView(
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
        LocalDateTime sentAt) {
}
