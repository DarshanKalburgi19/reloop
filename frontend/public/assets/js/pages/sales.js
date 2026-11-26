/*
 * My sales: GET /api/orders?role=seller + GET /api/providers/{accountId}/earnings (marketplace totals).
 * Sellers see gross, commission and net per order, can book the simulated delivery for paid orders and can issue a
 * refund (POST /api/transactions/{id}/refunds).
 */
RL.page(async ({ account }) => {
  const { $, $$, esc, money, dateTime } = RL.ui;
  const ui = RL.ui;
  let orders = [];
  let group = 'all';

  async function loadStats(quiet) {
    if (quiet !== true) ui.loader('#sales-stats', 'Loading earnings…');
    try {
      const e = await RL.endpoints.reports.providerEarnings(account.id);
      const t = (e.byTransactionType && e.byTransactionType.MARKETPLACE_PURCHASE) || e.totals || {};
      const col = (s) => `<div class="col-6 col-xl-3">${RL.cards.statCard(s)}</div>`;
      $('#sales-stats').innerHTML = [
        col({ label: 'Gross sales', value: money(t.grossPaise || 0), icon: 'bi-graph-up', sub: `${t.transactionCount || 0} paid order(s)` }),
        col({ label: 'ReLoop commission', value: money(t.commissionPaise || 0), icon: 'bi-percent', sub: 'Deducted from your gross' }),
        col({ label: 'Net earnings', value: money(t.currentNetPaise !== undefined ? t.currentNetPaise : t.netPaise || 0), icon: 'bi-wallet2', sub: t.refundedGrossPaise ? `after ${money(t.refundedGrossPaise)} refunded` : 'After commission and refunds' }),
        col({ label: 'Pending payout', value: money(t.pendingPayoutPaise || 0), icon: 'bi-hourglass-split', sub: `${money(t.paidOutPaise || 0)} paid out (simulated)` }),
      ].join('');
    } catch (err) {
      ui.error('#sales-stats', err, loadStats, 'Could not load your earnings');
    }
  }

  function tabs() {
    $('#filter-tabs').innerHTML = RL.orders.GROUPS.map((g) => `<li class="nav-item" role="presentation"><button type="button" role="tab" class="nav-link${g.id === group ? ' active' : ''}" aria-selected="${g.id === group}" data-group="${g.id}">${esc(g.label)} <span class="opacity-75">${orders.filter(g.match).length}</span></button></li>`).join('');
    $$('[data-group]').forEach((b) => b.addEventListener('click', () => { group = b.dataset.group; render(); }));
  }

  function render() {
    tabs();
    const g = RL.orders.GROUPS.find((x) => x.id === group);
    ui.table('#orders', [
      { label: 'Order', render: (o) => `<span class="fw-semibold mono">${esc(o.orderNumber)}</span><div class="small text-muted">${esc(dateTime(o.createdAt))}</div>` },
      { label: 'Item', render: (o) => esc(o.productTitle) },
      { label: 'Gross', className: 'num', render: (o) => money(o.subtotalPaise) },
      { label: 'Commission', className: 'num', render: (o) => `− ${money(o.providerCommissionPaise)}` },
      { label: 'Your net', className: 'num fw-semibold', render: (o) => money(o.sellerNetPaise) },
      { label: 'Status', render: (o) => RL.orders.statusHtml(o, 'seller') },
      { label: '', className: 'text-end text-nowrap', render: (o) => `
        ${o.status === 'CONFIRMED' ? `<button type="button" class="btn btn-primary btn-sm" data-ship="${esc(o.id)}"><i class="bi bi-truck"></i> Book delivery</button>` : ''}
        ${['CONFIRMED', 'PARTIALLY_REFUNDED'].includes(o.status) && RL.orders.successfulPayment(o) ? `<button type="button" class="btn btn-outline-danger btn-sm" data-refund="${esc(o.id)}">Refund</button>` : ''}
        <button type="button" class="btn btn-light btn-sm border" data-view="${esc(o.id)}">Details</button>` },
    ], orders.filter(g.match), group === 'all'
      ? { icon: 'bi-graph-up-arrow', title: 'No sales yet', text: 'When someone orders one of your listings, it appears here with your net earnings.', actionHref: '/customer/sell.html', actionLabel: 'List an item' }
      : { icon: 'bi-funnel', title: 'No orders in this view' });
    const find = (id) => orders.find((o) => String(o.id) === String(id));
    $$('[data-view]').forEach((b) => b.addEventListener('click', () => RL.orders.showDetail(find(b.dataset.view), { role: 'seller' })));
    $$('[data-ship]').forEach((b) => b.addEventListener('click', async () => {
      const created = await RL.tracking.bookDialog({ deliveryType: 'STORE_TO_CUSTOMER', orderId: Number(b.dataset.ship), needOrigin: true, needDestination: true, title: 'Book simulated delivery to the buyer' });
      if (created) window.location.href = '/customer/tracking.html?tn=' + encodeURIComponent(created.trackingNumber);
    }));
    $$('[data-refund]').forEach((b) => b.addEventListener('click', async () => {
      const pay = RL.orders.successfulPayment(find(b.dataset.refund));
      try {
        const tx = await ui.busy(b, () => RL.endpoints.transactions.get(pay.transactionId), 'Loading…');
        const updated = await RL.refund.open(tx);
        if (updated) { load(); loadStats(); }
      } catch (err) {
        ui.toastError(err, 'Could not load the payment');
      }
    }));
  }

  async function load() {
    ui.loader('#orders', 'Loading your sales…');
    try {
      orders = await RL.endpoints.orders.list('seller');
      live.mark(orders);
      render();
    } catch (err) {
      $('#filter-tabs').innerHTML = '';
      ui.error('#orders', err, load, 'Could not load your sales');
    }
  }
  // the buyer paying / failing / getting a refund changes this list and the earnings totals
  const live = ui.poll(() => RL.endpoints.orders.list('seller'), (data) => { orders = data; render(); loadStats(true); });
  loadStats();
  load();
});
