package com.reloop.repair.enums;

/**
 * Payment state of a repair request as reported by order-service (the source of truth for money). Kept next to the
 * workflow {@link RepairStatus} so both dashboards can show e.g. "PAID · refunded" or "AWAITING_PAYMENT · last
 * attempt failed" without calling order-service. {@code null} = no payment attempt yet.
 */
public enum RepairPaymentStatus {
    /** The latest attempt was declined (simulated); nothing was charged. */
    FAILED,
    /** The latest attempt was cancelled by the customer; nothing was charged. */
    CANCELLED,
    /** Paid in full (payment-confirmation). */
    PAID,
    /** Part of the payment was refunded; the job continues. */
    PARTIALLY_REFUNDED,
    /** Fully refunded; an unfinished job is closed (CANCELLED). */
    REFUNDED
}
