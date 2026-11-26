/*
 * My purchases: GET /api/orders?role=buyer. Pay (checkout), view breakdown, cancel a pending order
 * (POST /api/orders/{id}/cancel), receipts and simulated delivery tracking.
 */
RL.page(async () => {
  const { $, $$, esc, money, dateTime } = RL.ui;
  const ui = RL.ui;
  let orders = [];
  let group = ui.param('filter') || 'all';

  function tabs() {
    $('#filter-tabs').innerHTML = RL.orders.GROUPS.map((g) => {
      const n = orders.filter(g.match).length;
      return `<li class="nav-item" role="presentation"><button type="button" role="tab" class="nav-link${g.id === group ? ' active' : ''}" aria-selected="${g.id === group}" data-group="${g.id}">${esc(g.label)} <span class="opacity-75">${n}</span></button></li>`;
    }).join('');
    $$('[data-group]').forEach((b) => b.addEventListener('click', () => { group = b.dataset.group; render(); }));
  }

  function render() {
    tabs();
    const g = RL.orders.GROUPS.find((x) => x.id === group) || RL.orders.GROUPS[0];
    const rows = orders.filter(g.match);
    ui.table('#orders', [
      { label: 'Order', render: (o) => `<span class="fw-semibold mono">${esc(o.orderNumber)}</span><div class="small text-muted">${esc(dateTime(o.createdAt))}</div>` },
      { label: 'Item', render: (o) => o.productId ? `<a href="/product.html?id=${encodeURIComponent(o.productId)}">${esc(o.productTitle)}</a>` : esc(o.productTitle) },
      { label: 'Price', className: 'num', render: (o) => money(o.subtotalPaise) },
      { label: 'ReLoop fee', className: 'num', render: (o) => money(o.customerFeePaise) },
      { label: 'Total', className: 'num fw-semibold', render: (o) => money(o.totalPaise) },
      { label: 'Status', render: (o) => RL.orders.statusHtml(o, 'buyer') },
      { label: '', className: 'text-end text-nowrap', render: (o) => `
          ${o.status === 'PENDING_PAYMENT' ? `<a class="btn btn-primary btn-sm" href="/checkout.html?orderId=${encodeURIComponent(o.id)}">Pay</a>
            <button type="button" class="btn btn-outline-danger btn-sm" data-cancel="${esc(o.id)}">Cancel</button>` : ''}
          <button type="button" class="btn btn-light btn-sm border" data-view="${esc(o.id)}">Details</button>` },
    ], rows, group === 'all'
      ? { icon: 'bi-bag', title: 'No purchases yet', text: 'When you buy something on ReLoop it shows up here with its full price breakdown.', actionHref: '/customer/buy.html', actionLabel: 'Browse products' }
      : { icon: 'bi-funnel', title: 'No orders in this view', text: 'Try another filter.' });
    $$('[data-view]').forEach((b) => b.addEventListener('click', () => RL.orders.showDetail(orders.find((o) => String(o.id) === b.dataset.view), { role: 'buyer' })));
    $$('[data-cancel]').forEach((b) => b.addEventListener('click', () => cancel(b.dataset.cancel)));
  }

  async function cancel(id) {
    const o = orders.find((x) => String(x.id) === String(id));
    const res = await ui.prompt({
      title: 'Cancel order ' + o.orderNumber + '?',
      message: 'The item is released back to the marketplace. Nothing has been charged for a pending order.',
      label: 'Reason (optional)', field: 'reason', maxLength: 500, confirmLabel: 'Cancel order', confirmClass: 'btn-danger', busyLabel: 'Cancelling…',
      action: (reason) => RL.endpoints.orders.cancel(id, reason),
    });
    if (res) { ui.toast(`Order ${res.orderNumber} cancelled.`, 'success'); load(); }
  }

  async function load() {
    ui.loader('#orders', 'Loading your orders…');
    try {
      orders = await RL.endpoints.orders.list('buyer');
      live.mark(orders);
      render();
    } catch (err) {
      $('#filter-tabs').innerHTML = '';
      ui.error('#orders', err, load, 'Could not load your purchases');
    }
  }
  // a sale confirmed, refunded or cancelled elsewhere shows up without a reload
  const live = ui.poll(() => RL.endpoints.orders.list('buyer'), (data) => { orders = data; render(); });
  load();
});
