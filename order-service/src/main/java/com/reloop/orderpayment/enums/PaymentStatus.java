package com.reloop.orderpayment.enums;

/**
 * Outcome of ONE simulated payment attempt (stored as a string).
 * <ul>
 *   <li>{@link #SUCCESS}: money "taken"; only this confirms an order or repair booking and creates the commission,
 *       payout and ledger records.</li>
 *   <li>{@link #FAILED} / {@link #CANCELLED}: simulated decline / customer cancelled at the payment page. Recorded
 *       for the history only: no commission, no payout, no ledger entries; the order stays payable.</li>
 *   <li>{@link #PARTIALLY_REFUNDED} / {@link #REFUNDED}: a SUCCESS payment after simulated refunds.</li>
 * </ul>
 */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    CANCELLED,
    PARTIALLY_REFUNDED,
    REFUNDED;

    /** True for payments that took money (and therefore have commission, payout and ledger records). */
    public boolean isSettled() {
        return this == SUCCESS || this == PARTIALLY_REFUNDED || this == REFUNDED;
    }
}
