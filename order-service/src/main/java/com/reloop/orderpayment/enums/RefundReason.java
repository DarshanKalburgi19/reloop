package reloop.orderpayment.enums;

/**
 * RefundReason - fixed status/choice enum (stored as string, never ordinal).
 *
 * <p>Why a refund was issued.</p>
 */
public enum RefundReason {

    ORDER_CANCELLED,
    DISPUTED_ORDER,
    CUSTOMER_REQUEST

}
