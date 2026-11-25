package com.reloop.orderpayment.enums;

/**
 * ProviderType - fixed status/choice enum (stored as string, never ordinal).
 *
 * <p>Who receives the payout: a marketplace SELLER (Account.id) or REPAIRER (RepairerProfile.id).</p>
 */
public enum ProviderType {

    SELLER,
    REPAIRER

}
