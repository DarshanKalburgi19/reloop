/*
 * Customer overview. Loads in parallel and degrades per section:
 *   GET /api/orders?role=buyer, GET /api/repair-requests/mine, GET /api/products/mine,
 *   GET /api/customers/{accountId}/payments
 */
RL.page(async ({ account }) => {
  const { $, esc, money, badge, relative, dateTime } = RL.ui;
  const first = String(account.name || '').split(/\s+/)[0];
  $('#greeting').textContent = first ? `Hi, ${first}` : 'Overview';

  RL.ui.loader('#stats', 'Loading your summary…');
  RL.ui.loader('#recent-orders');
  RL.ui.loader('#recent-repairs');

  const ACTIVE_REPAIR = ['REQUESTED', 'ACCEPTED', 'INSPECTED', 'QUOTED', 'QUOTE_APPROVED', 'AWAITING_PAYMENT', 'PAID', 'IN_PROGRESS'];
  const fetchAll = () => Promise.allSettled([
    RL.endpoints.orders.list('buyer'),
    RL.endpoints.repairRequests.mine({ size: 5 }),
    RL.endpoints.repairRequests.mine({ status: ACTIVE_REPAIR, size: 50 }),
    RL.endpoints.products.mine({ size: 50 }),
    RL.endpoints.reports.customerPayments(account.id),
  ]);

  // Re-drawn by RL.poll whenever any section's data changes (each section still degrades on its own).
  function draw([orders, repairs, activeRepairs, listings, payments]) {

    const stat = (s) => `<div class="col-6 col-xl-3">${RL.cards.statCard(s)}</div>`;
    const failed = (r) => r.status === 'rejected';
    const statsHtml = [];
    statsHtml.push(stat(failed(orders) ? { label: 'Purchases', value: '—', icon: 'bi-receipt', sub: 'Could not load' }
      : { label: 'Purchases', value: RL.ui.number(orders.value.length), icon: 'bi-receipt', sub: `${orders.value.filter((o) => o.status === 'PENDING_PAYMENT').length} awaiting payment` }));
    statsHtml.push(stat(failed(activeRepairs) ? { label: 'Active repairs', value: '—', icon: 'bi-tools', sub: 'Could not load' }
      : { label: 'Active repairs', value: RL.ui.number(activeRepairs.value.totalElements), icon: 'bi-tools', sub: `${activeRepairs.value.content.filter((r) => ['QUOTED', 'AWAITING_PAYMENT'].includes(r.status)).length} need your decision` }));
    statsHtml.push(stat(failed(listings) ? { label: 'Active listings', value: '—', icon: 'bi-tags', sub: 'Could not load' }
      : { label: 'Active listings', value: RL.ui.number(listings.value.content.filter((p) => p.availability === 'ACTIVE').length), icon: 'bi-tags', sub: `${listings.value.totalElements} listed in total` }));
    statsHtml.push(stat(failed(payments) ? { label: 'Total paid', value: '—', icon: 'bi-credit-card', sub: 'Could not load' }
      : { label: 'Total paid', value: money(payments.value.totalPaidPaise), icon: 'bi-credit-card', sub: payments.value.totalRefundedPaise
        ? `${money(payments.value.totalRefundedPaise)} refunded · net ${money(payments.value.netSpentPaise)}` : `incl. ${money(payments.value.totalFeesPaise)} ReLoop fees` }));
    $('#stats').innerHTML = statsHtml.join('');

    // Things waiting for the customer.
    const todo = [];
    if (!failed(orders)) {
      orders.value.filter((o) => o.status === 'PENDING_PAYMENT').forEach((o) => todo.push(
        `<li class="list-group-item d-flex justify-content-between align-items-center gap-2"><span><i class="bi bi-hourglass-split text-warning me-2" aria-hidden="true"></i>Order <strong>${esc(o.orderNumber)}</strong> (${esc(o.productTitle)}) is waiting for payment of ${money(o.amountDuePaise || o.totalPaise)}</span>
          <a class="btn btn-sm btn-primary" href="/checkout.html?orderId=${encodeURIComponent(o.id)}">Pay now</a></li>`));
    }
    if (!failed(activeRepairs)) {
      activeRepairs.value.content.filter((r) => ['QUOTED', 'AWAITING_PAYMENT'].includes(r.status)).forEach((r) => todo.push(
        `<li class="list-group-item d-flex justify-content-between align-items-center gap-2"><span><i class="bi bi-chat-square-quote text-primary me-2" aria-hidden="true"></i>${r.status === 'QUOTED' ? 'New quote' : (r.paymentStatus === 'FAILED' || r.paymentStatus === 'CANCELLED' ? '<span class="text-danger">Payment did not go through</span>, try again' : 'Approved quote awaiting payment')} for <strong>${esc(r.itemDescription)}</strong> from ${esc(r.repairerBusinessName)}</span>
          <a class="btn btn-sm btn-primary" href="/customer/repair-request.html?id=${encodeURIComponent(r.id)}">${r.status === 'QUOTED' ? 'Review quote' : 'Pay now'}</a></li>`));
    }
    $('#attention').innerHTML = todo.length
      ? `<div class="card"><div class="card-header"><i class="bi bi-bell me-1" aria-hidden="true"></i>Needs your attention</div><ul class="list-group list-group-flush">${todo.join('')}</ul></div>` : '';

    if (failed(orders)) {
      RL.ui.error('#recent-orders', orders.reason, () => window.location.reload(), 'Could not load purchases');
    } else {
      RL.ui.table('#recent-orders', [
        { label: 'Order', render: (o) => `<button type="button" class="btn btn-link p-0 text-start fw-semibold" data-order="${esc(o.id)}">${esc(o.orderNumber)}</button><div class="small text-muted">${esc(relative(o.createdAt))}</div>` },
        { label: 'Item', render: (o) => esc(o.productTitle) },
        { label: 'Total', className: 'num', render: (o) => money(o.totalPaise) },
        { label: 'Status', render: (o) => RL.orders.statusHtml(o, 'buyer') },
      ], orders.value.slice(0, 5), { icon: 'bi-bag', title: 'No purchases yet', text: 'Items you buy will appear here.', actionHref: '/customer/buy.html', actionLabel: 'Browse products' });
      RL.ui.$$('[data-order]').forEach((b) => b.addEventListener('click', () => RL.orders.showDetail(Number(b.dataset.order), { role: 'buyer' })));
    }

    if (failed(repairs)) {
      RL.ui.error('#recent-repairs', repairs.reason, () => window.location.reload(), 'Could not load repair requests');
    } else {
      RL.ui.table('#recent-repairs', [
        { label: 'Request', render: (r) => `<a class="fw-semibold" href="/customer/repair-request.html?id=${encodeURIComponent(r.id)}">${esc(r.requestNumber)}</a><div class="small text-muted">${esc(dateTime(r.createdAt))}</div>` },
        { label: 'Item', render: (r) => `${esc(r.itemDescription)}<div class="small text-muted">${esc(r.repairerBusinessName)}</div>` },
        { label: 'Status', render: (r) => badge(r.status) + RL.ui.paymentBadge(r.paymentStatus) },
      ], repairs.value.content, { icon: 'bi-tools', title: 'No repair requests yet', text: 'Find a local workshop and request a repair quote.', actionHref: '/repairers.html', actionLabel: 'Find a repairer' });
    }
  }

  const initial = await fetchAll();
  draw(initial);
  RL.ui.poll(fetchAll, draw, { interval: 15000 }).mark(initial);
});
