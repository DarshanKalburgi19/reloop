package com.reloop.orderpayment.enums;

/** PAYMENT entries are posted by a SUCCESS payment; REFUND entries reverse them. */
public enum LedgerEntryType {
    PAYMENT,
    REFUND
}
