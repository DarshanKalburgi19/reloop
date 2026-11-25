package com.reloop.orderpayment.dto.quote;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/internal/repair-quotes}, sent by repair-service when a repairer prepares or revises
 * a quote. Only the repairer's price is sent; fees are computed here with the current configured rates.
 *
 * @param baseAmountPaise   repairer's price in paise (≥ ₹1, ≤ ₹10,00,000)
 * @param repairerAccountId product-service Account ID of the repairer's login (owner of the payout and earnings);
 *                          optional for older callers
 */
public record CreateRepairQuoteRequest(
        @NotNull @Positive Long repairRequestId,
        @Size(max = 40) String requestNumber,
        @NotNull @Positive Long repairerProfileId,
        @NotNull @Positive Long customerAccountId,
        @NotNull @Positive @Max(value = 100_000_000L, message = "must be at most 100000000 paise (Rs 10,00,000)")
        Long baseAmountPaise,
        @Size(max = 2000) String summary,
        @Positive Long repairerAccountId) {
}
