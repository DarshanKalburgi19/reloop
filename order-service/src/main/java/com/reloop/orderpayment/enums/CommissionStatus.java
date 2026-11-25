package com.reloop.orderpayment.enums;

/**
 * CommissionStatus - fixed status/choice enum (stored as string, never ordinal).
 *
 * <p>A commission ledger row is recorded on success and reversed on refund.</p>
 */
public enum CommissionStatus {

    RECORDED,
    REVERSED

}
