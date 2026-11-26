package com.reloop.orderpayment.enums;

/** Why a simulated refund was issued. */
public enum RefundReason {
    ORDER_CANCELLED,
    DISPUTED_ORDER,
    CUSTOMER_REQUEST,
    ITEM_NOT_AS_DESCRIBED,
    REPAIR_NOT_COMPLETED,
    OTHER
}
