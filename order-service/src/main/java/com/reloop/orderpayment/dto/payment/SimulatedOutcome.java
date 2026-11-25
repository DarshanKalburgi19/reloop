package com.reloop.orderpayment.dto.payment;

/** What the payment simulator should do. No real gateway is ever contacted. */
public enum SimulatedOutcome {
    SUCCESS,
    FAILED,
    CANCELLED
}
