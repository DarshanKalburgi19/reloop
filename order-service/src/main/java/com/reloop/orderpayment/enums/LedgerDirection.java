package com.reloop.orderpayment.enums;

public enum LedgerDirection {
    DEBIT,
    CREDIT;

    public LedgerDirection opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
