/*
 * Repair request detail, shared by /customer/repair-request.html and /repairer/repair-request.html.
 *
 *   GET  /api/repair-requests/{id}                      details, quote, allowedActions, history
 *   POST /api/repair-requests/{id}/<action>             workflow actions (only those in allowedActions are offered)
 *   POST /api/payments/simulate {repairQuoteId}         customer pays an approved quote (order-service)
 *   GET  /api/tracking/repair-requests/{id}/deliveries  simulated delivery legs
 *   GET  /api/inventory/repair-requests/{id}/parts      parts used (repairer)
 *
 * The quote is always shown with the full fee breakdown (repair amount, customer fee, total, repairer gross,
 * commission, net, ReLoop total). The customer must see it again and tick a confirmation before approving.
 *
 * Both parties see the payment state reported by order-service (paymentStatus, transactionId, lastPaymentFailure,
 * refundedAmountPaise) and the page polls GET /api/repair-requests/{id} (RL.poll) so a quote sent, a payment made
 * or a refund issued by the other side shows up without a manual reload.
 */
RL.page(async ({ role }) => {
  const { $, $$, esc, money, rupees, label, badge, dateTime, toPaise } = RL.ui;
  const ui = RL.ui;
  const R = RL.endpoints.repairRequests;
  const isCustomer = role === 'CUSTOMER';
  const id = ui.param('id');
  const root = $('#request');
  const listHref = isCustomer ? '/customer/repairs.html' : '/repairer/active.html';

  if (!id || !/^\d+$/.test(id)) {
    ui.error(root, new Error('No repair request was specified.'), null, 'Repair request not found');
    return;
  }

  let req = null;
  let legs = [];
  let legsError = null;
  let activeTn = null;

  const FLOW = ['REQUESTED', 'ACCEPTED', 'INSPECTED', 'QUOTED', 'AWAITING_PAYMENT', 'PAID', 'IN_PROGRESS', 'COMPLETED'];
  const STAMP = { REQUESTED: 'createdAt', ACCEPTED: 'acceptedAt', INSPECTED: 'inspectedAt', QUOTED: 'quotedAt', AWAITING_PAYMENT: 'approvedAt', PAID: 'paidAt', IN_PROGRESS: 'inProgressAt', COMPLETED: 'completedAt' };
  const CLOSED = ['REJECTED', 'CANCELLED', 'QUOTE_REJECTED'];

  const NEXT_STEP = {
    CUSTOMER: {
      REQUESTED: 'Waiting for the workshop to accept your request.',
      ACCEPTED: 'The workshop accepted. They will inspect the item next.',
      INSPECTED: 'Inspection done. A quote is on its way.',
      QUOTED: 'Review the quote below. Approve it to continue, or reject it.',
      QUOTE_APPROVED: 'Quote approved.',
      AWAITING_PAYMENT: 'Pay the approved quote to start the repair.',
      PAID: 'Paid. The workshop will start the repair.',
      IN_PROGRESS: 'The repair is in progress.',
      COMPLETED: 'The repair is complete.',
      QUOTE_REJECTED: 'You rejected the quote. The workshop may send a revised quote.',
      REJECTED: 'The workshop declined this request.',
      CANCELLED: 'This request was cancelled.',
    },
    REPAIRER: {
      REQUESTED: 'Accept or reject this request.',
      ACCEPTED: 'Inspect the item and record your findings.',
      INSPECTED: 'Send the customer a quote.',
      QUOTED: 'Waiting for the customer to approve or reject your quote.',
      QUOTE_APPROVED: 'Quote approved.',
      AWAITING_PAYMENT: 'The customer approved your quote and needs to pay.',
      PAID: 'Paid. Start the repair and post progress updates.',
      IN_PROGRESS: 'Post progress updates and mark the repair complete when done.',
      COMPLETED: 'Repair completed.',
      QUOTE_REJECTED: 'The customer rejected your quote. You can send a revised quote.',
      REJECTED: 'You declined this request.',
      CANCELLED: 'This request was cancelled.',
    },
  };

  // ------------------------------------------------------------ loading
  async function load() {
    ui.loader(root, 'Loading repair request…');
    try {
      req = await R.get(id);
    } catch (err) {
      ui.error(root, err, load, 'Could not load this repair request');
      return;
    }
    $('#crumb').textContent = req.requestNumber;
    document.title = req.requestNumber + ' — ReLoop';
    await loadLegs();
    render();
  }
  async function loadLegs() {
    try {
      legs = await RL.endpoints.tracking.forRepair(req.id);
      legsError = null;
    } catch (err) {
      legs = [];
      legsError = err;
    }
  }
  const can = (a) => (req.allowedActions || []).includes(a);

  // ------------------------------------------------------------ rendering
  /** Payment state as seen by both sides: failed attempts, the paid transaction, refunds. */
  function paymentHtml() {
    const ps = req.paymentStatus;
    if (!ps) return '';
    if (ps === 'FAILED' || ps === 'CANCELLED') {
      const what = ps === 'FAILED' ? 'The last payment attempt failed' : 'The last payment attempt was cancelled';
      const next = isCustomer
        ? 'Nothing was charged and the repair has not been confirmed. You can try again below.'
        : 'Nothing was charged, so this job is not confirmed and no payout was created. Wait for the customer to pay before starting work.';
      return `<div class="alert alert-danger d-flex gap-2 mb-0" role="status" data-payment-alert="${esc(ps)}"><i class="bi bi-exclamation-octagon" aria-hidden="true"></i>
        <div><strong>${esc(what)}.</strong> ${req.lastPaymentFailure ? esc(req.lastPaymentFailure) + '. ' : ''}${esc(next)}</div></div>`;
    }
    const tx = req.transactionId ? `<button type="button" class="btn btn-link p-0 mono" data-tx="${esc(req.transactionId)}">${esc(req.transactionId)}</button>` : '—';
    const refunded = req.refundedAmountPaise ? `<dt>Refunded to customer</dt><dd class="text-danger">${money(req.refundedAmountPaise)}</dd>` : '';
    const note = ps === 'REFUNDED'
      ? (isCustomer ? 'This payment was refunded in full.' : 'Refunded in full: your commission and payout for this job were reversed.')
      : ps === 'PARTIALLY_REFUNDED'
        ? (isCustomer ? 'Part of this payment was refunded.' : 'Partly refunded: your commission and payout were reduced in proportion.')
        : (isCustomer ? 'Payment received. The workshop has been notified.' : 'Payment confirmed. This job is paid; your payout is pending (simulated).');
    return `<div class="card" data-payment-card="${esc(ps)}"><div class="card-header d-flex justify-content-between align-items-center"><span><i class="bi bi-credit-card me-1" aria-hidden="true"></i>Payment</span>${badge(ps)}</div>
      <div class="card-body small"><dl class="dl-grid mb-2"><dt>Transaction</dt><dd>${tx}</dd>${req.paidAt ? `<dt>Paid</dt><dd>${esc(dateTime(req.paidAt))}</dd>` : ''}${refunded}</dl>
      <p class="mb-0 text-muted">${esc(note)}</p></div></div>`;
  }

  function progressHtml() {
    if (CLOSED.includes(req.status)) {
      return `<div class="alert alert-${req.status === 'QUOTE_REJECTED' ? 'warning' : 'secondary'} small mb-0">${badge(req.status)} <span class="ms-1">${esc(req.closeReason || '')}</span></div>`;
    }
    const idx = FLOW.indexOf(req.status === 'QUOTE_APPROVED' ? 'AWAITING_PAYMENT' : req.status);
    return `<ul class="timeline">${FLOW.map((s, i) => `<li class="${i <= idx ? '' : 'opacity-50'}"><div class="tl-title">${esc(label(s))}${i === idx ? ' <span class="small text-success">· current</span>' : ''}</div>
      <div class="tl-meta">${req[STAMP[s]] ? esc(dateTime(req[STAMP[s]])) : (i <= idx ? '' : 'Pending')}</div></li>`).join('')}</ul>`;
  }

  function historyHtml() {
    const h = req.history || [];
    if (!h.length) return '<p class="small text-muted mb-0">No history yet.</p>';
    return `<ul class="timeline">${h.slice().reverse().map((e) => `<li><div class="tl-title">${e.fromStatus ? esc(label(e.fromStatus)) + ' → ' : ''}${esc(label(e.toStatus))}</div>
      ${e.note ? `<div class="small">${esc(e.note)}</div>` : ''}<div class="tl-meta">${esc(label(e.actor))} · ${esc(dateTime(e.at))}</div></li>`).join('')}</ul>`;
  }

  function actionsHtml() {
    const btn = (action, text, cls, icon) => can(action) ? `<button type="button" class="btn ${cls}" data-action="${action}"><i class="bi ${icon} me-1" aria-hidden="true"></i>${esc(text)}</button>` : '';
    const buttons = isCustomer
      ? [btn('approve-quote', 'Review & approve quote', 'btn-primary', 'bi-check2-circle'), btn('reject-quote', 'Reject quote', 'btn-outline-danger', 'bi-x-circle'),
        btn('cancel', 'Cancel request', 'btn-outline-danger', 'bi-slash-circle')]
      : [btn('accept', 'Accept request', 'btn-primary', 'bi-check2'), btn('reject', 'Reject', 'btn-outline-danger', 'bi-x'),
        btn('inspect', 'Record inspection', 'btn-primary', 'bi-search'), btn('quote', 'Send quote', 'btn-primary', 'bi-receipt'),
        btn('revise-quote', 'Revise quote', 'btn-outline-primary', 'bi-pencil-square'), btn('progress', req.status === 'PAID' ? 'Start repair' : 'Post progress update', 'btn-primary', 'bi-wrench'),
        btn('complete', 'Mark complete', 'btn-success', 'bi-check2-all'), btn('cancel', 'Cancel request', 'btn-outline-danger', 'bi-slash-circle')];
    const html = buttons.filter(Boolean).join('');
    return `<div class="card"><div class="card-body">
      <div class="small-caps mb-1">Next step</div>
      <p class="mb-${html ? '3' : '0'}">${esc(NEXT_STEP[role][req.status] || label(req.status))}</p>
      ${html ? `<div class="d-flex flex-wrap gap-2">${html}</div>` : ''}
    </div></div>`;
  }

  function quoteHtml() {
    const q = req.quote;
    if (!q) return '';
    return `<div class="card" id="quote-card"><div class="card-header d-flex justify-content-between align-items-center flex-wrap gap-2">
        <span><i class="bi bi-receipt me-1" aria-hidden="true"></i>Quote${q.revision > 1 ? ` · revision ${esc(q.revision)}` : ''}</span>
        <span class="d-flex gap-2 align-items-center">${q.state ? badge(q.state) : ''}<span class="small text-muted">${esc(dateTime(q.sentAt))}</span></span></div>
      <div class="card-body d-grid gap-3">
        <div><div class="small-caps">Workshop's summary</div><p class="mb-1">${esc(q.summary)}</p>
          ${q.estimatedDays ? `<div class="small text-muted"><i class="bi bi-calendar3 me-1" aria-hidden="true"></i>Estimated ${esc(q.estimatedDays)} day(s)</div>` : ''}</div>
        ${RL.fees.html(RL.fees.fromQuote(q), { title: 'Quote breakdown', providerLabel: 'Repairer' })}
      </div></div>`;
  }

  function detailsHtml() {
    const imgs = (req.imageUrls || []).map((u) => ui.safeUrl(u)).filter(Boolean);
    return `<div class="card"><div class="card-header">Item and problem</div><div class="card-body">
      <dl class="dl-grid">
        <dt>Item</dt><dd>${esc(req.itemDescription)}</dd>
        <dt>Category</dt><dd>${esc(label(req.category))}</dd>
        ${req.repairServiceName ? `<dt>Service</dt><dd>${esc(req.repairServiceName)}</dd>` : ''}
        <dt>Hand-over</dt><dd>${esc(label(req.deliveryMethod))}</dd>
        ${req.deliveryInstructions ? `<dt>Instructions</dt><dd>${esc(req.deliveryInstructions)}</dd>` : ''}
        <dt>Problem</dt><dd class="text-pre-wrap">${esc(req.problemDescription)}</dd>
        ${req.customerNotes ? `<dt>Customer notes</dt><dd>${esc(req.customerNotes)}</dd>` : ''}
        ${req.inspectionNotes ? `<dt>Inspection findings</dt><dd>${esc(req.inspectionNotes)}</dd>` : ''}
      </dl>
      ${imgs.length ? `<div class="d-flex flex-wrap gap-2 mt-3">${imgs.map((u) => `<a href="${esc(u)}" target="_blank" rel="noopener"><img src="${esc(u)}" alt="Photo of the item" width="96" height="96" class="rounded border object-fit-cover bg-light"></a>`).join('')}</div>` : ''}
    </div></div>`;
  }

  function partyHtml() {
    const r = req.repairer || {};
    if (isCustomer) {
      return `<div class="card"><div class="card-header">Workshop</div><div class="card-body small">
        <div class="fw-semibold mb-1"><a href="/repairer.html?id=${encodeURIComponent(r.profileId)}">${esc(r.businessName)}</a></div>
        <div>${esc(r.addressLine || '')}</div><div>${esc(r.city || '')} ${esc(r.pincode || '')}</div>
        ${r.phone ? `<div class="mt-1"><i class="bi bi-telephone me-1" aria-hidden="true"></i><a href="tel:${esc(r.phone)}">${esc(r.phone)}</a></div>` : ''}
      </div></div>`;
    }
    return `<div class="card"><div class="card-header">Customer</div><div class="card-body small">
      <div>Customer account #${esc(req.customerAccountId)}</div>
      <div class="text-muted">Contact details are shared through ReLoop messages and deliveries.</div></div></div>`;
  }

  function deliveryBookingButtons() {
    if (req.deliveryMethod === 'IN_PERSON') return '';
    const has = (type) => legs.some((l) => l.deliveryType === type);
    const inbound = legs.find((l) => l.deliveryType === 'CUSTOMER_TO_REPAIRER');
    const out = [];
    if (req.status === 'ACCEPTED' && !has('CUSTOMER_TO_REPAIRER')) {
      out.push('<button type="button" class="btn btn-primary btn-sm" data-book="CUSTOMER_TO_REPAIRER"><i class="bi bi-truck me-1"></i>Book pickup to workshop</button>');
    }
    const canReturn = req.status === 'COMPLETED' || (['QUOTE_REJECTED', 'CANCELLED'].includes(req.status) && inbound && inbound.status === 'DELIVERED');
    if (!isCustomer && canReturn && !has('REPAIRER_TO_CUSTOMER')) {
      out.push('<button type="button" class="btn btn-primary btn-sm" data-book="REPAIRER_TO_CUSTOMER"><i class="bi bi-truck me-1"></i>Book return to customer</button>');
    }
    return out.join(' ');
  }

  function deliveriesHtml() {
    const booking = deliveryBookingButtons();
    let body;
    if (legsError) body = ui.errorHtml(legsError, 'Could not load deliveries');
    else if (!legs.length) body = `<p class="small text-muted mb-0">${req.deliveryMethod === 'IN_PERSON' ? 'Hand-over is in person at the workshop, so no delivery is needed.' : 'No simulated delivery booked yet.'}</p>`;
    else body = RL.tracking.legsHtml(legs, activeTn) + '<div class="mt-3" data-leg-detail></div>';
    return `<div class="card"><div class="card-header d-flex justify-content-between align-items-center flex-wrap gap-2"><span><i class="bi bi-truck me-1" aria-hidden="true"></i>Deliveries (simulated)</span><span class="d-flex gap-2">${booking}</span></div>
      <div class="card-body">${body}</div></div>`;
  }

  function render() {
    const showPay = isCustomer && can('pay') && req.quote;
    root.innerHTML = `
      <div class="rl-page-header">
        <div><h1 class="d-flex flex-wrap align-items-center gap-2">${esc(req.requestNumber)} ${badge(req.status)}${ui.paymentBadge(req.paymentStatus)}</h1>
          <p>${esc(req.itemDescription)} · ${esc(isCustomer ? (req.repairer && req.repairer.businessName) : 'Customer #' + req.customerAccountId)} · requested ${esc(dateTime(req.createdAt))}</p></div>
        <div><a class="btn btn-light border" href="${listHref}"><i class="bi bi-arrow-left me-1" aria-hidden="true"></i>Back</a>
          <button type="button" class="btn btn-light border" data-reload><i class="bi bi-arrow-clockwise" aria-hidden="true"></i><span class="visually-hidden">Refresh</span></button></div>
      </div>
      <div class="row g-4">
        <div class="col-xl-8 d-grid gap-4 align-content-start">
          ${actionsHtml()}
          ${paymentHtml()}
          ${showPay ? `<div class="row g-3" id="pay-section"><div class="col-lg-6">${RL.fees.html(RL.fees.fromQuote(req.quote), { title: 'You are paying', providerLabel: 'Repairer' })}</div><div class="col-lg-6" id="pay-form"></div></div>` : quoteHtml()}
          ${detailsHtml()}
          ${deliveriesHtml()}
          ${!isCustomer ? '<div id="parts-card"></div>' : ''}
        </div>
        <div class="col-xl-4 d-grid gap-4 align-content-start">
          <div class="card"><div class="card-header">Progress</div><div class="card-body">${progressHtml()}</div></div>
          ${partyHtml()}
          <div class="card"><div class="card-header">History</div><div class="card-body">${historyHtml()}</div></div>
        </div>
      </div>`;

    $('[data-reload]', root).addEventListener('click', load);
    // the repairer (the payment's provider) can refund from here; order-service then reports it back to this request
    $$('[data-tx]', root).forEach((b) => b.addEventListener('click', () => RL.transactions.showDetail(b.dataset.tx,
      { asProvider: !isCustomer, allowRefund: !isCustomer, onChange: () => live.refresh() })));
    $$('[data-action]', root).forEach((b) => b.addEventListener('click', () => runAction(b.dataset.action, b)));
    $$('[data-book]', root).forEach((b) => b.addEventListener('click', () => book(b.dataset.book)));
    $$('[data-tn]', root).forEach((b) => b.addEventListener('click', () => showLeg(b.dataset.tn)));
    if (activeTn) showLeg(activeTn);
    else if (legs.length) showLeg(legs[legs.length - 1].trackingNumber);

    if (showPay) {
      RL.paymentForm.render($('#pay-form'), {
        amountPaise: toPaise(req.quote.totalPayable),
        customerFeePaise: toPaise(req.quote.customerFee),
        target: { repairQuoteId: req.quote.quoteId },
        title: 'Pay for this repair (simulated)',
        onResult: (tx) => {
          if (tx.status === 'SUCCESS') ui.toast('The workshop has been notified that you paid.', 'info');
          // failed / cancelled attempts are recorded on the request too (paymentStatus), so refresh either way
          window.setTimeout(() => live.refresh(), 600);
        },
      });
    }
    if (!isCustomer) loadParts();
  }

  async function showLeg(tn) {
    activeTn = tn;
    $$('[data-tn]', root).forEach((b) => b.classList.toggle('active', b.dataset.tn === tn));
    const host = $('[data-leg-detail]', root);
    if (!host) return;
    ui.loader(host, 'Loading delivery…');
    try {
      const d = await RL.endpoints.tracking.get(tn);
      RL.tracking.renderDetail(host, d, {
        onChange: async () => { await loadLegs(); },
      });
    } catch (err) {
      ui.error(host, err, () => showLeg(tn), 'Could not load this delivery');
    }
  }

  async function book(type) {
    const inbound = legs.find((l) => l.deliveryType === 'CUSTOMER_TO_REPAIRER');
    const created = await RL.tracking.bookDialog(type === 'CUSTOMER_TO_REPAIRER'
      ? { deliveryType: type, repairRequestId: req.id, needOrigin: true, needDestination: false, title: 'Book simulated pickup to the workshop' }
      : { deliveryType: type, repairRequestId: req.id, needOrigin: false, needDestination: !inbound, title: 'Book simulated return to the customer' });
    if (created) {
      activeTn = created.trackingNumber;
      await loadLegs();
      render();
    }
  }

  // ------------------------------------------------------------ actions
  function quoteDialog(revise) {
    const q = req.quote;
    let rate = null;
    return ui.modal({
      title: revise ? 'Revise quote' : 'Send quote',
      size: 'lg',
      confirmLabel: revise ? 'Send revised quote' : 'Send quote',
      busyLabel: 'Sending…',
      body: `<div class="row g-4"><div class="col-md-6">
          <div class="mb-3"><label class="form-label required" for="q-amount">Repair amount (₹)</label>
            <input class="form-control" id="q-amount" name="amount" type="number" min="1" max="1000000" step="0.01" required value="${q && revise ? esc(q.repairAmount) : ''}">
            <div class="invalid-feedback">Between ₹1 and ₹10,00,000.</div>
            <div class="form-text">Your price before ReLoop fees. The customer's platform fee is added on top and shown to them.</div></div>
          <div class="mb-3"><label class="form-label required" for="q-summary">What the repair includes</label>
            <textarea class="form-control" id="q-summary" name="summary" rows="4" minlength="10" maxlength="2000" required>${q && revise ? esc(q.summary) : ''}</textarea>
            <div class="invalid-feedback">10–2000 characters.</div></div>
          <div><label class="form-label" for="q-days">Estimated days</label>
            <input class="form-control" id="q-days" name="estimatedDays" type="number" min="1" max="60" value="${q && revise && q.estimatedDays ? esc(q.estimatedDays) : ''}"></div>
        </div><div class="col-md-6" data-preview><div class="rl-loader"><span class="spinner-border" aria-hidden="true"></span><span>Loading fee rates…</span></div></div></div>`,
      onRender: (el) => {
        const preview = el.querySelector('[data-preview]');
        const amount = el.querySelector('#q-amount');
        const draw = () => {
          if (!rate) return;
          const v = Number(amount.value);
          preview.innerHTML = v >= 1
            ? RL.fees.html(RL.fees.estimate(Math.round(v * 100), rate, 'REPAIR_BOOKING'), { title: 'What the customer will see', providerLabel: 'Repairer' })
            : '<div class="surface-muted p-3 small text-muted">Enter an amount to preview the customer total, your commission and your net earnings.</div>';
        };
        RL.fees.rates().then((m) => { rate = m.REPAIR_BOOKING || null; if (!rate) preview.innerHTML = '<div class="alert alert-warning small">Repair fee rates are not published; the exact breakdown is calculated when you send the quote.</div>'; else draw(); })
          .catch((err) => { preview.innerHTML = ui.errorHtml(err, 'Could not load fee rates'); });
        amount.addEventListener('input', draw);
      },
      onSubmit: (form) => {
        const v = ui.formValues(form);
        const body = { amount: Number(v.amount), summary: v.summary, estimatedDays: v.estimatedDays ? Number(v.estimatedDays) : null };
        return revise ? R.reviseQuote(req.id, body) : R.quote(req.id, body);
      },
    });
  }

  function approveDialog() {
    const q = req.quote;
    return ui.modal({
      title: 'Approve this quote?',
      size: 'lg',
      confirmLabel: `Approve ${rupees(q.totalPayable)}`,
      busyLabel: 'Approving…',
      body: `<p>${esc(q.summary)}</p>
        ${RL.fees.html(RL.fees.fromQuote(q), { title: 'Quote breakdown', providerLabel: 'Repairer' })}
        <div class="form-check mt-3"><input class="form-check-input" type="checkbox" id="ap-agree" required>
          <label class="form-check-label" for="ap-agree">I have read the breakdown and agree to pay <strong>${rupees(q.totalPayable)}</strong>, including the ReLoop customer platform fee of <strong>${rupees(q.customerFee)}</strong>.</label>
          <div class="invalid-feedback">Please confirm you have read the breakdown.</div></div>
        <p class="small text-muted mt-2 mb-0">After approving you will be asked to pay (simulated). Nothing is charged until then.</p>`,
      onSubmit: () => R.approveQuote(req.id),
    });
  }

  async function runAction(action, btn) {
    let result = null;
    try {
      switch (action) {
        case 'accept':
          if (!(await ui.confirm({ title: 'Accept this request?', message: 'The customer will be told you accepted. You will inspect the item next.', confirmLabel: 'Accept request' }))) return;
          result = await ui.busy(btn, () => R.accept(req.id), 'Accepting…');
          break;
        case 'reject':
          result = await ui.prompt({ title: 'Reject request', label: 'Reason for the customer', required: true, confirmLabel: 'Reject request', confirmClass: 'btn-danger', action: (n) => R.reject(req.id, n) });
          break;
        case 'inspect':
          result = await ui.prompt({ title: 'Record inspection', label: 'Inspection findings', required: true, minLength: 10, rows: 4, confirmLabel: 'Save findings', field: 'findings', action: (n) => R.inspect(req.id, n) });
          break;
        case 'quote':
          result = await quoteDialog(false);
          break;
        case 'revise-quote':
          result = await quoteDialog(true);
          break;
        case 'progress':
          result = await ui.prompt({ title: req.status === 'PAID' ? 'Start the repair' : 'Progress update', label: 'Update for the customer', required: true, confirmLabel: 'Post update', action: (n) => R.progress(req.id, n) });
          break;
        case 'complete':
          result = await ui.prompt({ title: 'Mark repair complete', label: 'Completion note (optional)', confirmLabel: 'Mark complete', confirmClass: 'btn-success', action: (n) => R.complete(req.id, n) });
          break;
        case 'cancel':
          result = await ui.prompt({ title: 'Cancel this request?', label: 'Reason', required: true, confirmLabel: 'Cancel request', confirmClass: 'btn-danger', message: 'Cancelling is only possible before payment.', action: (n) => R.cancel(req.id, n) });
          break;
        case 'approve-quote':
          result = await approveDialog();
          break;
        case 'reject-quote':
          result = await ui.prompt({ title: 'Reject quote', label: 'Tell the workshop why (optional)', confirmLabel: 'Reject quote', confirmClass: 'btn-danger', action: (n) => R.rejectQuote(req.id, n) });
          break;
        default:
          return;
      }
    } catch (err) {
      ui.toastError(err, 'Action failed');
      return;
    }
    if (!result) return;
    if (result && result.id) req = result;
    ui.toast(`Request is now "${label(req.status)}".`, 'success');
    await loadLegs();
    render();
    if (action === 'approve-quote') {
      const pay = $('#pay-section');
      if (pay) pay.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  // ------------------------------------------------------------ parts (repairer)
  async function loadParts() {
    const host = $('#parts-card');
    if (!host) return;
    host.innerHTML = `<div class="card"><div class="card-header d-flex justify-content-between align-items-center"><span><i class="bi bi-box-seam me-1" aria-hidden="true"></i>Parts used</span>
      <button type="button" class="btn btn-sm btn-outline-primary" data-use-part><i class="bi bi-plus-lg"></i> Use a part</button></div><div class="card-body" data-parts-body></div></div>`;
    const body = $('[data-parts-body]', host);
    $('[data-use-part]', host).addEventListener('click', usePart);
    ui.loader(body, 'Loading parts…');
    try {
      const u = await RL.endpoints.inventory.repairUsage(req.id);
      if (!u.movements || !u.movements.length) {
        body.innerHTML = '<p class="small text-muted mb-0">No spare parts recorded against this repair. Parts costs are your own and are not added to the customer\'s bill.</p>';
        return;
      }
      body.innerHTML = `<div class="table-responsive"><table class="table rl-table"><thead><tr><th>Part</th><th>Movement</th><th class="num">Qty</th><th class="num">Cost</th><th>When</th></tr></thead><tbody>
        ${u.movements.map((m) => `<tr><td>${esc(m.sparePartName)}${m.sku ? `<div class="small text-muted mono">${esc(m.sku)}</div>` : ''}</td><td>${esc(label(m.movementType))}</td><td class="num">${esc(m.quantityChange)}</td><td class="num">${money(m.totalCostPaise)}</td><td class="small">${esc(dateTime(m.movementDate))}</td></tr>`).join('')}
        </tbody><tfoot><tr><th colspan="2">Total (${esc(u.netUnitsUsed)} unit(s))</th><th></th><th class="num">${money(u.partsCostPaise)}</th><th></th></tr></tfoot></table></div>`;
    } catch (err) {
      ui.error(body, err, loadParts, 'Could not load parts used');
    }
  }

  async function usePart() {
    let parts;
    try {
      parts = (await RL.endpoints.inventory.parts({ size: 100 })).content.filter((p) => p.status === 'ACTIVE');
    } catch (err) {
      ui.toastError(err, 'Could not load your inventory');
      return;
    }
    if (!parts.length) {
      ui.toast('Your inventory has no active parts. Add parts on the Inventory page first.', 'warning');
      return;
    }
    const res = await ui.modal({
      title: 'Use a part for ' + req.requestNumber,
      confirmLabel: 'Record usage',
      body: `<div class="mb-3"><label class="form-label required" for="up-part">Part</label>
          <select class="form-select" id="up-part" name="partId" required>${ui.options(parts.map((p) => ({ value: p.id, label: `${p.partName} (${p.quantity} in stock)` })), '', 'Choose a part')}</select>
          <div class="invalid-feedback">Choose a part.</div></div>
        <div class="mb-3"><label class="form-label required" for="up-qty">Quantity</label><input class="form-control" id="up-qty" name="quantity" type="number" min="1" value="1" required></div>
        <div><label class="form-label" for="up-note">Note</label><input class="form-control" id="up-note" name="note" maxlength="500"></div>`,
      onSubmit: (form) => {
        const v = ui.formValues(form);
        return RL.endpoints.inventory.use(v.partId, { repairRequestId: req.id, quantity: Number(v.quantity), note: v.note || null });
      },
    });
    if (res) {
      ui.toast(res.alertMessage || 'Part usage recorded.', res.lowStockAlert ? 'warning' : 'success');
      loadParts();
    }
  }

  // ------------------------------------------------------------ live refresh
  // Re-render only when something the user can see changed: never while the pay form or a dialog is in use.
  const significant = (r) => JSON.stringify([r.status, r.paymentStatus, r.transactionId, r.refundedAmountPaise,
    r.lastPaymentFailure, (r.history || []).length, r.quote && [r.quote.quoteId, r.quote.state], r.allowedActions]);
  const live = ui.poll(() => R.get(id), async (fresh) => {
    if (!req) return;
    const changed = significant(fresh) !== significant(req);
    const statusChanged = fresh.status !== req.status;
    req = fresh;
    if (!changed) return;
    if (statusChanged) {
      ui.toast(`${req.requestNumber} is now "${label(req.status)}".`, 'info');
      await loadLegs();
    }
    render();
  }, { interval: 8000 });

  load();
});
