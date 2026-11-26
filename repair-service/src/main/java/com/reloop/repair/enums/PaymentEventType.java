package com.reloop.repair.enums;

/** What order-service reports through {@code POST /api/internal/repair-requests/{id}/payment-events}. */
public enum PaymentEventType {
    FAILED,
    CANCELLED,
    PARTIALLY_REFUNDED,
    REFUNDED
}
