package com.reloop.orderpayment.dto.report;

import java.util.List;
import java.util.Map;

/**
 * Seller / repairer dashboard: totals overall and per transaction type, plus one line per successful payment.
 *
 * @param providerAccountId product-service Account ID of the provider (the caller)
 */
public record ProviderEarningsView(
        Long providerAccountId,
        String currency,
        EarningsTotals totals,
        Map<String, EarningsTotals> byTransactionType,
        List<PayoutView> transactions) {
}
