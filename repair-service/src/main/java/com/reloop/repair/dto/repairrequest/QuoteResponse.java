package com.reloop.repair.dto.repairrequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The current quote with its full, transparent fee split (rupees). {@code state}:
 * AWAITING_APPROVAL, APPROVED, REJECTED, PAID or WITHDRAWN (request cancelled before payment).
 */
public record QuoteResponse(
        Long quoteId,
        int revision,
        String state,
        BigDecimal repairAmount,
        BigDecimal customerFee,
        BigDecimal totalPayable,
        String totalPayableLabel,
        BigDecimal repairerCommission,
        BigDecimal repairerNet,
        String summary,
        Integer estimatedDays,
        LocalDateTime sentAt) {
}
