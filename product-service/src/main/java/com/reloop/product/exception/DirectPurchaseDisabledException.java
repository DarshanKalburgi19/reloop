package com.reloop.product.exception;

/** {@code POST /api/products/{id}/purchase} is switched off; purchases go through order-service. Mapped to 410. */
public class DirectPurchaseDisabledException extends RuntimeException {

    public DirectPurchaseDisabledException() {
        super("Direct purchase is disabled. Place an order with order-service: POST /api/orders {\"productId\":...}, "
                + "then pay it with POST /api/payments/simulate");
    }
}
