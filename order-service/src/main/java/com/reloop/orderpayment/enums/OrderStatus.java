package com.reloop.orderpayment.enums;

/**
 * OrderStatus - fixed status/choice enum (stored as string, never ordinal).
 *
 * <p>Purchase order lifecycle. A failed payment leaves the order PENDING (unpaid).</p>
 */
public enum OrderStatus {

    PENDING,
    PAID,
    CANCELLED,
    REFUNDED

}
