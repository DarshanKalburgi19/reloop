package com.reloop.orderpayment.dto.order;

import java.time.LocalDateTime;

/**
 * Minimal, money-free view of a marketplace order for other ReLoop services
 * ({@code GET /api/internal/orders/{id}}). repair-service uses it to check that a
 * store-to-customer delivery is booked by the order's seller for a paid order.
 *
 * @param status the order status ({@code PENDING_PAYMENT}, {@code CONFIRMED}, {@code CANCELLED},
 *               {@code PARTIALLY_REFUNDED} or {@code REFUNDED})
 */
public record InternalOrderView(
        Long id,
        String orderNumber,
        String status,
        Long buyerAccountId,
        Long sellerAccountId,
        Long productId,
        String productTitle,
        LocalDateTime paidAt) {
}
