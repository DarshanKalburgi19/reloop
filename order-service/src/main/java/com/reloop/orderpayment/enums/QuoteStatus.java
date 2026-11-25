package com.reloop.orderpayment.enums;

/**
 * QuoteStatus - fixed status/choice enum (stored as string, never ordinal).
 *
 * <p>Repair quote lifecycle. Customer must APPROVE before payment.</p>
 */
public enum QuoteStatus {

    DRAFT,
    SENT,
    APPROVED,
    REJECTED,
    SUPERSEDED

}
