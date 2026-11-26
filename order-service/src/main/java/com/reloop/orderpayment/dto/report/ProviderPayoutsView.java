package com.reloop.orderpayment.dto.report;

import java.util.List;

/** Provider payouts list (simulated: payouts are records only, no money is transferred). */
public record ProviderPayoutsView(Long providerAccountId, int count, long pendingPayoutPaise, long paidOutPaise,
                                  List<PayoutView> payouts) {
}
