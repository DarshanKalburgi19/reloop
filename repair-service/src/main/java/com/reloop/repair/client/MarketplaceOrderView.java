package com.reloop.repair.client;

import java.time.LocalDateTime;

/**
 * order-service's {@code InternalOrderView} ({@code GET /api/internal/orders/{id}}): who bought and sold a
 * marketplace item and whether it is paid. No amounts. Unknown fields are ignored.
 */
public record MarketplaceOrderView(
        Long id,
        String orderNumber,
        String status,
        Long buyerAccountId,
        Long sellerAccountId,
        Long productId,
        String productTitle,
        LocalDateTime paidAt) {

    /** Paid and not refunded/cancelled: the only state in which a store-to-customer delivery may be booked. */
    public boolean isConfirmed() {
        return "CONFIRMED".equals(status);
    }
}
