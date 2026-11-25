package reloop.orderpayment.enums;

/**
 * PaymentStatus - fixed status/choice enum (stored as string, never ordinal).
 *
 * <p>Success, failure and cancellation must have three different outcomes.</p>
 */
public enum PaymentStatus {

    PENDING,
    SUCCESS,
    FAILURE,
    CANCELLED

}
