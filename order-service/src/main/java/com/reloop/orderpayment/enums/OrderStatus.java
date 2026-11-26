package com.reloop.orderpayment.enums;

/**
 * Marketplace order lifecycle (stored as a string).
 * <pre>
 * PENDING_PAYMENT ──SUCCESS payment──▶ CONFIRMED ──refund──▶ PARTIALLY_REFUNDED / REFUNDED
 *        │
 *        └──cancel / reservation expired──▶ CANCELLED
 * </pre>
 * Failed or cancelled payment attempts leave the order in PENDING_PAYMENT (it can be paid again until it expires).
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLED,
    PARTIALLY_REFUNDED,
    REFUNDED
}
