package reloop.orderpayment.enums;

/**
 * RefundStatus - fixed status/choice enum (stored as string, never ordinal).
 *
 * <p>Simulated refund lifecycle.</p>
 */
public enum RefundStatus {

    INITIATED,
    COMPLETED,
    FAILED

}
