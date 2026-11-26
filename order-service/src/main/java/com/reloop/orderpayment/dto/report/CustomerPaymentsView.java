package com.reloop.orderpayment.dto.report;

import com.reloop.orderpayment.dto.payment.TransactionView;
import java.util.List;

/**
 * Customer dashboard: every payment attempt (newest first) and totals.
 *
 * @param totalPaidPaise     sum of totalCharged over settled payments (SUCCESS / PARTIALLY_REFUNDED / REFUNDED)
 * @param totalFeesPaise     customer fees paid in those payments
 * @param totalRefundedPaise amount returned by refunds
 * @param netSpentPaise      totalPaid − totalRefunded
 */
public record CustomerPaymentsView(
        Long customerAccountId,
        int count,
        int successfulCount,
        int failedCount,
        int cancelledCount,
        long totalPaidPaise,
        long totalFeesPaise,
        long totalRefundedPaise,
        long netSpentPaise,
        String currency,
        List<TransactionView> payments) {
}
