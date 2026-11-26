/*
 * Repairer earnings: GET /api/providers/{accountId}/earnings. Shows gross, ReLoop commission and net per payment, and
 * explains how a quote turns into net earnings using the live repair fee rates.
 */
RL.page(async ({ account }) => {
  const { $, esc, money, label, badge, dateTime, percent } = RL.ui;
  const ui = RL.ui;
  const col = (s) => `<div class="col-6 col-xl-3">${RL.cards.statCard(s)}</div>`;

  async function explainer() {
    try {
      const rates = await RL.fees.rates();
      const r = rates.REPAIR_BOOKING;
      if (!r) return;
      $('#earn-explainer').innerHTML = `<div class="card h-100"><div class="card-header">How a ₹1,000 repair is split</div><div class="card-body">
        ${RL.fees.html(RL.fees.estimate(100000, r, 'REPAIR_BOOKING'), { title: 'Example: ₹1,000 quote', note: 'Example only.' })}
        <p class="small text-muted mb-0 mt-2">The customer pays your quote plus a ${esc(percent(r.customerFeeRate))} ReLoop platform fee. ReLoop deducts a ${esc(percent(r.providerCommissionRate))} commission from your quote. Exact amounts are fixed on each quote.</p></div></div>`;
    } catch (err) {
      ui.error('#earn-explainer', err, explainer, 'Could not load the current fee rates');
    }
  }

  async function load() {
    ui.loader('#earn-stats', 'Loading earnings…');
    ui.loader('#earn-table', 'Loading payments…');
    $('#by-type').innerHTML = '';
    try {
      const e = await RL.endpoints.reports.providerEarnings(account.id);
      const t = e.totals || {};
      $('#earn-stats').innerHTML = [
        col({ label: 'Gross repair income', value: money(t.grossPaise || 0), icon: 'bi-graph-up', sub: `${t.transactionCount || 0} paid repair(s)` }),
        col({ label: 'ReLoop commission', value: money(t.commissionPaise || 0), icon: 'bi-percent', sub: t.reversedCommissionPaise ? `${money(t.reversedCommissionPaise)} reversed by refunds` : 'Deducted from your gross' }),
        col({ label: 'Net earnings', value: money(t.currentNetPaise || 0), icon: 'bi-wallet2', sub: t.refundedGrossPaise ? `after ${money(t.refundedGrossPaise)} refunded` : 'After commission and refunds' }),
        col({ label: 'Pending payout', value: money(t.pendingPayoutPaise || 0), icon: 'bi-hourglass-split', sub: `${money(t.paidOutPaise || 0)} paid out (simulated)` }),
      ].join('');
      const types = Object.entries(e.byTransactionType || {});
      $('#by-type').innerHTML = `<div class="card h-100"><div class="card-header">Breakdown</div>
        ${types.length ? `<div class="table-responsive"><table class="table rl-table mb-0"><thead><tr><th>Type</th><th class="num">Gross</th><th class="num">Commission</th><th class="num">Net now</th></tr></thead><tbody>
          ${types.map(([k, v]) => `<tr><td>${esc(label(k))}<div class="small text-muted">${v.transactionCount} payment(s)</div></td><td class="num">${money(v.grossPaise)}</td><td class="num">− ${money(v.commissionPaise)}</td><td class="num fw-semibold">${money(v.currentNetPaise)}</td></tr>`).join('')}
          <tr class="fw-semibold"><td>Total</td><td class="num">${money(t.grossPaise || 0)}</td><td class="num">− ${money(t.commissionPaise || 0)}</td><td class="num">${money(t.currentNetPaise || 0)}</td></tr>
        </tbody></table></div>` : ui.emptyHtml({ icon: 'bi-bar-chart', title: 'No earnings yet', text: 'Earnings appear once a customer pays an approved quote.' })}</div>`;
      ui.table('#earn-table', [
        { label: 'Payment', render: (p) => `<span class="mono fw-semibold">${esc(p.transactionId)}</span><div class="small text-muted">${esc(dateTime(p.createdAt))}</div>` },
        { label: 'Type', render: (p) => `<span class="small">${esc(label(p.transactionType))}</span>` },
        { label: 'Gross', className: 'num', render: (p) => money(p.grossAmountPaise) },
        { label: 'Commission', className: 'num', render: (p) => `− ${money(p.commissionPaise)}` },
        { label: 'Refunded', className: 'num', render: (p) => p.refundedGrossPaise ? `− ${money(p.refundedGrossPaise)}` : '<span class="text-muted">—</span>' },
        { label: 'Net now', className: 'num fw-semibold', render: (p) => money(p.currentNetPaise) },
        { label: 'Payout', render: (p) => `${badge(p.status)}${p.paidAt ? `<div class="small text-muted">${esc(dateTime(p.paidAt))}</div>` : ''}` },
      ], e.transactions || [], { icon: 'bi-cash-stack', title: 'No paid repairs yet', text: 'When a customer pays an approved quote, the payment and your net earnings appear here.', actionHref: '/repairer/active.html', actionLabel: 'View active repairs' });
    } catch (err) {
      ui.error('#earn-stats', err, load, 'Could not load your earnings');
      $('#earn-table').innerHTML = '';
    }
  }
  explainer();
  load();
});
