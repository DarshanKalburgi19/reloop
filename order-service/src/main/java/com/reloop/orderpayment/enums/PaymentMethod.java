package com.reloop.orderpayment.enums;

/**
 * PaymentMethod - fixed status/choice enum (stored as string, never ordinal).
 *
 * <p>Simulated payment methods offered in the checkout dialog.</p>
 */
public enum PaymentMethod {

    UPI,
    CARD,
    NET_BANKING,
    WALLET

}
