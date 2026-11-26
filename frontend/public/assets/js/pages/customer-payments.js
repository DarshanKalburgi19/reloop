/* Payment history: GET /api/customers/{accountId}/payments (summary + every attempt with its full breakdown). */
RL.page(async ({ account }) => {
  const { $, $$, esc, money, label, badge, dateTime } = RL.ui;
  const ui = RL.ui;

  async function load() {
    ui.loader('#pay-stats', 'Loading…');
    ui.loader('#payments', 'Loading your payments…');
    let data;
    try {
      data = await RL.endpoints.reports.customerPayments(account.id);
    } catch (err) {
      $('#pay-stats').innerHTML = '';
      ui.error('#payments', err, load, 'Could not load your payment history');
      return;
    }
    live.mark(data);
    draw(data);
  }

  function draw(data) {
    const col = (s) => `<div class="col-6 col-xl-3">${RL.cards.statCard(s)}</div>`;
    $('#pay-stats').innerHTML = [
      col({ label: 'Total paid', value: money(data.totalPaidPaise), icon: 'bi-credit-card', sub: `${data.successfulCount} successful payment(s)` }),
      col({ label: 'ReLoop fees paid', value: money(data.totalFeesPaise), icon: 'bi-percent', sub: 'Customer platform fees' }),
      col({ label: 'Refunded', value: money(data.totalRefundedPaise), icon: 'bi-arrow-counterclockwise', sub: 'Returned to you' }),
      col({ label: 'Net spent', value: money(data.netSpentPaise), icon: 'bi-wallet2', sub: `${data.failedCount} failed · ${data.cancelledCount} cancelled` }),
    ].join('');

    const list = data.payments || [];
    ui.table('#payments', [
      { label: 'Payment', render: (t) => `<span class="mono small fw-semibold">${esc(t.transactionId)}</span><div class="small text-muted">${esc(dateTime(t.attemptedAt))}</div>` },
      { label: 'For', render: (t) => `${esc(label(t.transactionType))}<div class="small text-muted">${t.orderNumber ? esc(t.orderNumber) : t.repairRequestId ? `<a href="/customer/repair-request.html?id=${encodeURIComponent(t.repairRequestId)}">Repair request #${esc(t.repairRequestId)}</a>` : ''}</div>` },
      { label: 'Method', render: (t) => esc(label(t.method)) },
      { label: 'Base', className: 'num', render: (t) => money(t.baseAmountPaise) },
      { label: 'ReLoop fee', className: 'num', render: (t) => money(t.customerFeePaise) },
      { label: 'Total charged', className: 'num fw-semibold', render: (t) => money(t.totalChargedPaise) },
      { label: 'Status', render: (t) => badge(t.status)
        + (['FAILED', 'CANCELLED'].includes(t.status) ? '<div class="small text-muted">Nothing charged · not confirmed</div>' : '')
        + (t.refundedAmountPaise ? `<div class="small text-muted">${money(t.refundedAmountPaise)} refunded</div>` : '') },
      { label: '', className: 'text-end text-nowrap', render: (t) => `<button type="button" class="btn btn-light btn-sm border" data-tx="${esc(t.transactionId)}">Details</button>` },
    ], list, { icon: 'bi-credit-card-2-front', title: 'No payments yet', text: 'Payments for purchases and repairs appear here with their full fee breakdown.' });
    $$('[data-tx]').forEach((b) => b.addEventListener('click', () => RL.transactions.showDetail(list.find((t) => t.transactionId === b.dataset.tx))));
  }
  // refunds issued by a seller / workshop show up here without a reload
  const live = ui.poll(() => RL.endpoints.reports.customerPayments(account.id), draw, { interval: 15000 });
  load();
});
