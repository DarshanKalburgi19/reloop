package com.reloop.orderpayment.dto.report;

/**
 * Provider earnings totals over successful payments (failed / cancelled attempts never count).
 *
 * @param grossPaise               sum of base prices (provider gross)
 * @param commissionPaise          ReLoop commission deducted
 * @param netPaise                 gross − commission, before refunds
 * @param refundedGrossPaise       gross returned to customers by refunds
 * @param reversedCommissionPaise  commission given back on refunds
 * @param reversedNetPaise         net taken back by refunds
 * @param currentNetPaise          net − reversed net: what the provider is owed / has earned now
 * @param pendingPayoutPaise       current net of PENDING / ON_HOLD / PARTIALLY_REVERSED payouts
 * @param paidOutPaise             current net of PAID payouts
 */
public record EarningsTotals(
        long transactionCount,
        long grossPaise,
        long commissionPaise,
        long netPaise,
        long refundedGrossPaise,
        long reversedCommissionPaise,
        long reversedNetPaise,
        long currentNetPaise,
        long pendingPayoutPaise,
        long paidOutPaise) {
}
