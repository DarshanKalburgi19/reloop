package reloop.orderpayment.enums;

/**
 * PayoutStatus - fixed status/choice enum (stored as string, never ordinal).
 *
 * <p>Simulated provider payout lifecycle (never real money).</p>
 */
public enum PayoutStatus {

    PENDING,
    PAID,
    ON_HOLD,
    REVERSED

}
