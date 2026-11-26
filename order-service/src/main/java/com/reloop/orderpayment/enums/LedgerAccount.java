package com.reloop.orderpayment.enums;

/**
 * Accounts of the simulated double-entry ledger. For every SUCCESS payment:
 * <pre>
 * DEBIT  CUSTOMER_PAYMENTS            customer total (base + customer fee)
 * CREDIT PROVIDER_PAYABLE             provider net   (base − provider commission)
 * CREDIT PLATFORM_FEE_REVENUE         customer fee
 * CREDIT PLATFORM_COMMISSION_REVENUE  provider commission
 * </pre>
 * Refunds post the exact opposite entries (linked with {@code reversalOfEntryId}), so after a full refund every
 * account nets to zero for that payment.
 */
public enum LedgerAccount {
    CUSTOMER_PAYMENTS,
    PROVIDER_PAYABLE,
    PLATFORM_FEE_REVENUE,
    PLATFORM_COMMISSION_REVENUE
}
