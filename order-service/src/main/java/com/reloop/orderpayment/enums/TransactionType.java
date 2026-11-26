package com.reloop.orderpayment.enums;

/** What a simulated payment pays for. Stored as a string. */
public enum TransactionType {
    /** A second-hand product bought on the marketplace (provider = SELLER). */
    MARKETPLACE_PURCHASE,
    /** An approved repair quote (provider = REPAIRER). */
    REPAIR_BOOKING;

    public ProviderType providerType() {
        return this == MARKETPLACE_PURCHASE ? ProviderType.SELLER : ProviderType.REPAIRER;
    }
}
