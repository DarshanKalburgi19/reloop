/*
 * Checkout: GET /api/orders/{id}, then the exact fee breakdown recorded on the order (RL.fees.fromOrder) and the
 * simulated payment form (POST /api/payments/simulate with {orderId}). Only PENDING_PAYMENT orders can be paid; any
 * other status shows what happened instead of a pay button.
 */
RL.page(async () => {
  const { $, esc, money, label, badge, dateTime, relative } = RL.ui;
  const ui = RL.ui;
  const host = $('#checkout');
  const orderId = ui.param('orderId');
  if (!orderId || !/^\d+$/.test(orderId)) {
    host.innerHTML = `<div class="card">${ui.emptyHtml({ icon: 'bi-cart-x', title: 'No order selected', text: 'Start from a product page, or pay a pending order from your purchases.', actionHref: '/customer/purchases.html', actionLabel: 'My purchases' })}</div>`;
    return;
  }

  function summaryHtml(o) {
    return `<div class="card"><div class="card-header d-flex justify-content-between align-items-center"><span>Order <span class="mono">${esc(o.orderNumber)}</span></span>${badge(o.status)}</div>
      <ul class="list-group list-group-flush">${(o.items || []).map((i) => `<li class="list-group-item d-flex justify-content-between gap-3">
        <a href="/product.html?id=${encodeURIComponent(i.productId)}">${esc(i.title)}</a><span class="num">${i.quantity > 1 ? `${i.quantity} × ` : ''}${money(i.lineTotalPaise)}</span></li>`).join('')}</ul>
      <div class="card-body small text-muted">Placed ${esc(dateTime(o.createdAt))}${o.status === 'PENDING_PAYMENT' && o.expiresAt ? ` · reserved for you until ${esc(dateTime(o.expiresAt))} (${esc(relative(o.expiresAt))})` : ''}</div></div>`;
  }

  function paidHtml(o, tx) {
    return `<div class="card border-success"><div class="card-body text-center p-4 p-lg-5">
      <div class="display-6 text-success mb-2"><i class="bi bi-check-circle-fill" aria-hidden="true"></i></div>
      <h2 class="h4">Payment successful</h2>
      <p class="text-muted mb-1">You paid <strong>${money(tx ? tx.totalChargedPaise : o.totalPaise)}</strong> for order <span class="mono">${esc(o.orderNumber)}</span>.</p>
      ${tx ? `<p class="small text-muted">Transaction <span class="mono">${esc(tx.transactionId)}</span> · simulated, no real money was moved.</p>` : ''}
      <div class="d-flex flex-wrap justify-content-center gap-2 mt-3">
        ${tx ? '<button type="button" class="btn btn-light border" id="receipt"><i class="bi bi-file-earmark-text me-1" aria-hidden="true"></i>View receipt</button>' : ''}
        <a class="btn btn-primary" href="/customer/purchases.html">Go to my purchases</a></div>
      <p class="small text-muted mt-3 mb-0">The seller will book delivery. You can follow it under Tracking.</p>
    </div></div>`;
  }

  async function load() {
    ui.loader(host, 'Loading your order…');
    let o;
    try {
      o = await RL.endpoints.orders.get(orderId);
    } catch (err) {
      if (err.status === 404) {
        host.innerHTML = `<div class="card">${ui.emptyHtml({ icon: 'bi-cart-x', title: 'Order not found', text: 'This order does not exist or belongs to another account.', actionHref: '/customer/purchases.html', actionLabel: 'My purchases' })}</div>`;
        return;
      }
      ui.error(host, err, load, 'Could not load your order');
      return;
    }
    const breakdown = RL.fees.fromOrder(o);
    host.innerHTML = `<div class="row g-4">
      <div class="col-lg-6 d-grid gap-4 align-content-start"><div>${summaryHtml(o)}</div><div id="fees"></div></div>
      <div class="col-lg-6" id="pay"></div></div>`;
    RL.fees.render('#fees', breakdown, { title: 'Exact price breakdown' });
    const pay = $('#pay');
    if (o.status === 'PENDING_PAYMENT') {
      RL.paymentForm.render(pay, {
        amountPaise: o.amountDuePaise || o.totalPaise,
        customerFeePaise: o.customerFeePaise,
        target: { orderId: o.id },
        title: 'Pay for this order (simulated)',
        onResult: async (tx) => {
          if (tx.status !== 'SUCCESS') return; // the form shows FAILED / CANCELLED itself and stays open
          let fresh = o;
          try { fresh = await RL.endpoints.orders.get(o.id); } catch (e) { /* show success with what we have */ }
          host.innerHTML = paidHtml(fresh, tx);
          $('#receipt').addEventListener('click', (ev) => ui.openReceipt(tx.transactionId, ev.currentTarget));
          window.scrollTo(0, 0);
        },
      });
      return;
    }
    const paid = (o.payments || []).find((p) => ['SUCCESS', 'PARTIALLY_REFUNDED', 'REFUNDED'].includes(p.status));
    const messages = {
      CONFIRMED: ['bi-check-circle', 'success', 'This order is already paid.'],
      PARTIALLY_REFUNDED: ['bi-arrow-counterclockwise', 'info', 'This order was paid and partly refunded.'],
      REFUNDED: ['bi-arrow-counterclockwise', 'info', 'This order was paid and fully refunded.'],
      CANCELLED: ['bi-x-circle', 'secondary', `This order was cancelled${o.cancelReason ? ': ' + o.cancelReason : ''}. The item is no longer reserved for you.`],
    };
    const [icon, tone, text] = messages[o.status] || ['bi-info-circle', 'secondary', `Order status: ${label(o.status)}.`];
    pay.innerHTML = `<div class="alert alert-${tone}"><i class="bi ${icon} me-1" aria-hidden="true"></i>${esc(text)}</div>
      <div class="d-flex gap-2">${paid ? '<button type="button" class="btn btn-light border" id="receipt">View receipt</button>' : ''}
      ${o.status === 'CANCELLED' && o.productId ? `<a class="btn btn-primary" href="/product.html?id=${encodeURIComponent(o.productId)}">Back to the product</a>` : '<a class="btn btn-primary" href="/customer/purchases.html">My purchases</a>'}</div>`;
    if (paid) $('#receipt').addEventListener('click', (ev) => ui.openReceipt(paid.transactionId, ev.currentTarget));
  }
  load();
});
