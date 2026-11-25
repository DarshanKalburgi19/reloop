package com.reloop.repair.client;

/**
 * JSON sent to order-service {@code POST /api/internal/repair-quotes/{quoteId}/approve} or {@code /reject}.
 * order-service re-checks that the quote belongs to this repair request and customer and is still open.
 */
public record QuoteDecisionCommand(Long repairRequestId, Long customerAccountId, String note) {
}
