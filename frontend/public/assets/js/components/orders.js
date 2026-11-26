/*
 * Marketplace order helpers (order-service OrderView):
 *   RL.orders.GROUPS                       filter tabs used by purchases and sales
 *   RL.orders.showDetail(orderOrId, {role: 'buyer'|'seller', onChange})
 *   RL.orders.successfulPayment(order)     the SUCCESS payment attempt (for receipts / refunds)
 */
(function (window) {
  'use strict';

  const RL = window.RL = window.RL || {};
  const { esc, money, label, badge, dateTime } = RL.ui;

  const GROUPS = [
    { id: 'all', label: 'All', match: () => true },
    { id: 'pending', label: 'Pending payment', match: (o) => o.status === 'PENDING_PAYMENT' },
    { id: 'confirmed', label: 'Paid', match: (o) => o.status === 'CONFIRMED' },
    { id: 'closed', label: 'Cancelled & refunded', match: (o) => ['CANCELLED', 'REFUNDED', 'PARTIALLY_REFUNDED'].includes(o.status) },
  ];

  function successfulPayment(o) {
    return (o.payments || []).find((p) => ['SUCCESS', 'PARTIALLY_REFUNDED', 'REFUNDED'].includes(p.status)) || null;
  }

  function attemptsHtml(list) {
    if (!list || !list.length) return '<p class="small text-muted mb-0">No payment attempts yet.</p>';
    return `<div class="table-responsive"><table class="table rl-table"><thead><tr><th>Transaction</th><th>Method</th><th>Status</th><th class="num">Amount</th><th>When</th><th></th></tr></thead><tbody>
      ${list.map((p) => `<tr><td class="mono small">${esc(p.transactionId)}</td><td>${esc(label(p.method))}</td><td>${badge(p.status)}${p.failureReason ? `<div class="small text-muted">${esc(p.failureReason)}</div>` : ''}</td>
        <td class="num">${money(p.amountPaise)}</td><td class="small">${esc(dateTime(p.attemptedAt))}</td>
        <td class="text-end">${['SUCCESS', 'PARTIALLY_REFUNDED', 'REFUNDED'].includes(p.status) ? `<button type="button" class="btn btn-light btn-sm border" data-receipt="${esc(p.transactionId)}">Receipt</button>` : ''}</td></tr>`).join('')}
    </tbody></table></div>`;
  }

  async function showDetail(orderOrId, opts) {
    const o = opts || {};
    let order = typeof orderOrId === 'object' ? orderOrId : null;
    try {
      if (!order) order = await RL.endpoints.orders.get(orderOrId);
    } catch (err) {
      RL.ui.toastError(err, 'Could not load the order');
      return;
    }
    const seller = o.role === 'seller';
    const items = (order.items && order.items.length ? order.items : [{ title: order.productTitle, unitPricePaise: order.subtotalPaise, quantity: 1, lineTotalPaise: order.subtotalPaise }]);
    RL.ui.modal({
      title: 'Order ' + order.orderNumber,
      size: 'lg',
      footer: `${order.status === 'PENDING_PAYMENT' && !seller ? `<a class="btn btn-primary me-auto" href="/checkout.html?orderId=${encodeURIComponent(order.id)}">Pay ${money(order.amountDuePaise || order.totalPaise)}</a>` : ''}
        <button type="button" class="btn btn-light border" data-bs-dismiss="modal">Close</button>`,
      body: `<div class="d-flex flex-wrap justify-content-between gap-2 mb-3">
          <div><div class="small text-muted">Placed ${esc(dateTime(order.createdAt))}</div>
            ${order.paidAt ? `<div class="small text-muted">Paid ${esc(dateTime(order.paidAt))}</div>` : ''}
            ${order.status === 'PENDING_PAYMENT' && order.expiresAt ? `<div class="small text-warning-emphasis">Reserved until ${esc(dateTime(order.expiresAt))}</div>` : ''}
            ${order.cancelledAt ? `<div class="small text-muted">Cancelled ${esc(dateTime(order.cancelledAt))}${order.cancelReason ? ': ' + esc(order.cancelReason) : ''}</div>` : ''}</div>
          <div>${badge(order.status)}</div></div>
        <div class="table-responsive mb-3"><table class="table rl-table"><thead><tr><th>Item</th><th class="num">Unit price</th><th class="num">Qty</th><th class="num">Line total</th></tr></thead><tbody>
          ${items.map((i) => `<tr><td>${i.productId ? `<a href="/product.html?id=${encodeURIComponent(i.productId)}">${esc(i.title)}</a>` : esc(i.title)}</td><td class="num">${money(i.unitPricePaise)}</td><td class="num">${esc(i.quantity)}</td><td class="num">${money(i.lineTotalPaise)}</td></tr>`).join('')}
        </tbody></table></div>
        ${RL.fees.html(RL.fees.fromOrder(order), { title: seller ? 'Sale breakdown' : 'Order breakdown' })}
        ${order.lastPaymentFailure ? `<div class="alert alert-warning small mt-3 mb-0">Last payment attempt failed: ${esc(order.lastPaymentFailure)}</div>` : ''}
        <h3 class="h6 mt-4">Payment attempts</h3>${attemptsHtml(order.payments)}
        <h3 class="h6 mt-4">Delivery (simulated)</h3><div data-deliveries><div class="rl-loader py-3"><span class="spinner-border" aria-hidden="true"></span><span>Loading deliveries…</span></div></div>`,
      onRender: (el) => {
        el.querySelectorAll('[data-receipt]').forEach((b) => b.addEventListener('click', () => RL.ui.openReceipt(b.dataset.receipt, b)));
        const host = el.querySelector('[data-deliveries]');
        RL.endpoints.tracking.forOrder(order.id).then((legs) => {
          host.innerHTML = legs && legs.length
            ? RL.tracking.legsHtml(legs) + '<a class="small d-inline-block mt-2" href="/customer/tracking.html">Open tracking</a>'
            : `<p class="small text-muted mb-0">${order.status === 'CONFIRMED' ? (seller ? 'No delivery booked yet. Use "Book delivery" in the sales list.' : 'The seller has not booked a delivery yet.') : 'A delivery can be booked once the order is paid.'}</p>`;
          host.querySelectorAll('[data-tn]').forEach((b) => b.addEventListener('click', () => {
            window.location.href = '/customer/tracking.html?tn=' + encodeURIComponent(b.dataset.tn);
          }));
        }).catch((err) => { host.innerHTML = RL.ui.errorHtml(err, 'Could not load deliveries'); });
      },
    });
  }

  RL.orders = { GROUPS, showDetail, successfulPayment };
})(window);
