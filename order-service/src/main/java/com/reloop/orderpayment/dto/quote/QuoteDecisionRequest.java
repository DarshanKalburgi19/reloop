package com.reloop.orderpayment.dto.quote;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/internal/repair-quotes/{id}/approve|reject}. repair-service has already checked the
 * caller is the request's customer; the ids are re-checked here against the stored quote.
 */
public record QuoteDecisionRequest(
        @NotNull @Positive Long repairRequestId,
        @NotNull @Positive Long customerAccountId,
        @Size(max = 1000) String note) {
}
