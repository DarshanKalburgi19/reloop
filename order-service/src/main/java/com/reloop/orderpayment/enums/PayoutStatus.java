package com.reloop.orderpayment.enums;

/**
 * Simulated payout owed to a provider for one successful payment. Created only for SUCCESS payments.
 * Refunds move it to PARTIALLY_REVERSED / REVERSED.
 */
public enum PayoutStatus {
    PENDING,
    PAID,
    ON_HOLD,
    PARTIALLY_REVERSED,
    REVERSED
}
