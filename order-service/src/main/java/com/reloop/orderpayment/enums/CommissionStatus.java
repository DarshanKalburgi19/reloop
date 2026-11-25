package com.reloop.orderpayment.enums;

/** ReLoop's commission on one payment; refunds reverse it proportionally. */
public enum CommissionStatus {
    RECORDED,
    PARTIALLY_REVERSED,
    REVERSED
}
