package com.reloop.orderpayment.client;

/**
 * JSON body of product-service {@code POST /api/internal/products/{id}/reserve | confirm-sale | release}.
 *
 * @param buyerAccountId product-service Account ID of the buyer (null for release)
 * @param orderReference order number that owns the reservation (idempotency key on the product side)
 * @param transactionId  successful payment's transaction id (confirm-sale only)
 * @param reason         why a reservation is released (release only)
 */
public record ProductReservationCommand(Long buyerAccountId, String orderReference, String transactionId, String reason) {
}
