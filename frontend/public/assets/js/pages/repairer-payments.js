/*
 * Repairer payment history: GET /api/providers/{accountId}/payouts. Each row opens the full transaction
 * (GET /api/transactions/{id}) with breakdown, receipt and refund (POST /api/transactions/{id}/refunds).
 */
RL.page(async ({ account }) => {
  const { $, $$, esc, money, label, badge, dateTime } = RL.ui;
  const ui = RL.ui;
  const col = (s) => `<div class="col-6 col-xl-4">${RL.cards.statCard(s)}</div>`;

  async function load() {
    ui.loader('#payouts', 'Loading payments…');
    try {
      const r = await RL.endpoints.reports.providerPayouts(account.id);
      $('#payout-stats').innerHTML = [
        col({ label: 'Payments received', value: String(r.count), icon: 'bi-receipt' }),
        col({ label: 'Pending payout', value: money(r.pendingPayoutPaise || 0), icon: 'bi-hourglass-split', sub: 'Simulated' }),
        col({ label: 'Paid out', value: money(r.paidOutPaise || 0), icon: 'bi-bank', sub: 'Simulated' }),
      ].join('');
      ui.table('#payouts', [
        { label: 'Payment', render: (p) => `<span class="mono fw-semibold">${esc(p.transactionId)}</span><div class="small text-muted">${esc(dateTime(p.createdAt))} · ${esc(p.payoutReference)}</div>` },
        { label: 'Type', render: (p) => `<span class="small">${esc(label(p.transactionType))}</span>` },
        { label: 'Gross', className: 'num', render: (p) => money(p.grossAmountPaise) },
        { label: 'Commission', className: 'num', render: (p) => `− ${money(p.commissionPaise - (p.reversedCommissionPaise || 0))}` },
        { label: 'Net', className: 'num fw-semibold', render: (p) => money(p.currentNetPaise) },
        { label: 'Payout', render: (p) => badge(p.status) },
        { label: '', className: 'text-end', render: (p) => `<button type="button" class="btn btn-sm btn-light border" data-tx="${esc(p.transactionId)}">Details</button>` },
      ], r.payouts || [], { icon: 'bi-credit-card-2-front', title: 'No payments yet', text: 'Customer payments for your repairs appear here with their payout status.' });
      $$('[data-tx]').forEach((b) => b.addEventListener('click', async () => {
        try {
          const tx = await ui.busy(b, () => RL.endpoints.transactions.get(b.dataset.tx), 'Loading…');
          RL.transactions.showDetail(tx, { allowRefund: true, onChange: load });
        } catch (err) { ui.toastError(err, 'Could not load the payment'); }
      }));
    } catch (err) {
      ui.error('#payouts', err, load, 'Could not load your payment history');
    }
  }
  load();
});
