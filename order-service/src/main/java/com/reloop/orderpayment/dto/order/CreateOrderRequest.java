package com.reloop.orderpayment.dto.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Body of {@code POST /api/orders}: buy one ACTIVE marketplace listing. The price, seller and fees come from the
 * server (product-service + configured rates), never from the client.
 */
public record CreateOrderRequest(@NotNull @Positive Long productId) {
}
