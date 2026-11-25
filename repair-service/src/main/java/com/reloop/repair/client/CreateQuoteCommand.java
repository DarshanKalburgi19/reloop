package com.reloop.repair.client;

/**
 * JSON sent to order-service {@code POST /api/internal/repair-quotes}. order-service applies the configured
 * customer fee and repairer commission rates, supersedes the request's previous open quote, and returns
 * the new revision.
 */
public record CreateQuoteCommand(
        Long repairRequestId,
        String requestNumber,
        Long repairerProfileId,
        Long customerAccountId,
        long baseAmountPaise,
        String summary) {
}
