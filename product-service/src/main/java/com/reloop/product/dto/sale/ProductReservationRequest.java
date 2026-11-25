package com.reloop.product.dto.sale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body of order-service's internal calls {@code POST /api/internal/products/{id}/reserve | confirm-sale | release}.
 *
 * @param buyerAccountId Account ID of the buyer (required for reserve and confirm-sale)
 * @param orderReference order-service order number owning the reservation (idempotency key)
 * @param transactionId  successful payment's transaction id (confirm-sale, recorded in the passport history)
 * @param reason         why the reservation is released (release)
 */
public record ProductReservationRequest(
        @Positive Long buyerAccountId,
        @NotBlank @Size(max = 64) String orderReference,
        @Size(max = 64) String transactionId,
        @Size(max = 500) String reason) {
}
