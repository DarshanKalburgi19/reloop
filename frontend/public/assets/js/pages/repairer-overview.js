/*
 * Repairer business overview: GET /api/repairers/me (listing status + profile-completion prompt),
 * GET /api/repair-requests/incoming, GET /api/providers/{accountId}/earnings, GET /api/inventory/low-stock.
 * Each panel loads independently, so one unavailable service does not blank the whole dashboard.
 */
RL.page(async ({ account }) => {
  const { $, esc, money, label, badge, relative } = RL.ui;
  const ui = RL.ui;
  const P = RL.repairerProfile;
  $('#greeting').textContent = `Hi, ${String(account.name || '').split(' ')[0] || 'there'}`;
  const NEW = ['REQUESTED'];
  const ACTIVE = ['ACCEPTED', 'INSPECTED', 'QUOTED', 'QUOTE_APPROVED', 'AWAITING_PAYMENT', 'PAID', 'IN_PROGRESS'];
  const col = (s) => `<div class="col-6 col-xl-3">${RL.cards.statCard(s)}</div>`;

  async function listing() {
    ui.loader('#listing-status', 'Checking your workshop profile…');
    try {
      const d = await P.me(true);
      if (!d) { $('#listing-status').innerHTML = '<div class="alert alert-danger mb-0">No workshop profile is linked to this account.</div>'; return; }
      $('#public-link').href = '/repairer.html?id=' + encodeURIComponent(d.id);
      const missing = d.missingForListing || [];
      if (!missing.length) {
        $('#listing-status').innerHTML = `<div class="alert alert-success d-flex align-items-center gap-2 mb-0"><i class="bi bi-patch-check-fill" aria-hidden="true"></i>
          <div><strong>${esc(d.businessName)}</strong> is listed in customer search. ${d.verificationStatus === 'VERIFIED' ? 'Verified by ReLoop.' : 'Verification: ' + esc(label(d.verificationStatus)) + '.'}</div></div>`;
        return;
      }
      const sections = [...new Set(missing.map((m) => P.SECTION_OF[m]).filter(Boolean))];
      const steps = ['business', 'services', 'location', 'hours'];
      const done = steps.filter((s) => !sections.includes(s)).length;
      const pct = Math.round((done / steps.length) * 100);
      $('#listing-status').innerHTML = `<div class="card border-warning"><div class="card-body">
        <div class="d-flex flex-wrap justify-content-between gap-3 align-items-start">
          <div><h2 class="h5 mb-1"><i class="bi bi-eye-slash text-warning me-1" aria-hidden="true"></i>Complete your profile to appear in search</h2>
            <p class="text-muted mb-0">Customers can't find <strong>${esc(d.businessName)}</strong> until you add: ${esc(P.missingText(missing))}.</p></div>
          <a class="btn btn-primary" href="${esc(P.SECTION_PAGE[sections[0]])}">Continue setup</a>
        </div>
        <div class="progress mt-3" role="progressbar" aria-label="Profile completion" aria-valuenow="${pct}" aria-valuemin="0" aria-valuemax="100" style="height:.5rem"><div class="progress-bar bg-success" style="width:${pct}%"></div></div>
        <ul class="list-plain row g-2 mt-2 small">${steps.map((s) => `<li class="col-sm-6 col-lg-3">
          <a class="d-flex align-items-center gap-2 text-decoration-none ${sections.includes(s) ? '' : 'text-muted'}" href="${esc(P.SECTION_PAGE[s])}">
            <i class="bi ${sections.includes(s) ? 'bi-circle text-warning' : 'bi-check-circle-fill text-success'}" aria-hidden="true"></i>${esc(P.SECTION_TITLE[s])}</a></li>`).join('')}</ul>
      </div></div>`;
    } catch (err) {
      ui.error('#listing-status', err, listing, 'Could not load your workshop profile');
    }
  }

  async function requests(quiet) {
    if (quiet !== true) ui.loader('#recent', 'Loading requests…');
    try {
      const [fresh, active, done] = await Promise.all([
        RL.endpoints.repairRequests.incoming({ status: NEW, size: 5 }),
        RL.endpoints.repairRequests.incoming({ status: ACTIVE, size: 1 }),
        RL.endpoints.repairRequests.incoming({ status: ['COMPLETED'], size: 1 }),
      ]);
      $('#stats').insertAdjacentHTML('afterbegin', [
        col({ label: 'New requests', value: String(fresh.totalElements), icon: 'bi-inbox', sub: 'Waiting for your answer' }),
        col({ label: 'Active repairs', value: String(active.totalElements), icon: 'bi-wrench-adjustable', sub: 'Accepted, not yet completed' }),
        col({ label: 'Completed', value: String(done.totalElements), icon: 'bi-check2-square', sub: 'Repairs finished' }),
      ].join(''));
      ui.table('#recent', [
        { label: 'Request', render: (r) => `<a class="fw-semibold mono" href="/repairer/repair-request.html?id=${encodeURIComponent(r.id)}">${esc(r.requestNumber)}</a><div class="small text-muted">${esc(relative(r.createdAt))}</div>` },
        { label: 'Item', render: (r) => `${esc(r.itemDescription)}<div class="small text-muted">${esc(label(r.category))} · ${esc(label(r.deliveryMethod))}</div>` },
        { label: 'Status', render: (r) => badge(r.status) + ui.paymentBadge(r.paymentStatus) },
        { label: '', className: 'text-end', render: (r) => `<a class="btn btn-sm btn-primary" href="/repairer/repair-request.html?id=${encodeURIComponent(r.id)}">Review</a>` },
      ], fresh.content, { icon: 'bi-inbox', title: 'No new requests', text: 'New repair requests from customers appear here.', actionHref: '/repairer/active.html', actionLabel: 'View active repairs' });
    } catch (err) {
      ui.error('#recent', err, requests, 'Could not load repair requests');
    }
  }

  async function earnings(quiet) {
    if (quiet !== true) ui.loader('#earnings-card', 'Loading earnings…');
    try {
      const e = await RL.endpoints.reports.providerEarnings(account.id);
      const t = (e.byTransactionType && e.byTransactionType.REPAIR_BOOKING) || e.totals || {};
      $('#stats').insertAdjacentHTML('beforeend', col({ label: 'Net earnings', value: money(t.currentNetPaise || 0), icon: 'bi-wallet2', sub: 'After ReLoop commission' }));
      $('#earnings-card').innerHTML = `<div class="card"><div class="card-header d-flex justify-content-between"><span>Earnings</span><a class="small" href="/repairer/earnings.html">Details</a></div>
        <div class="card-body"><dl class="dl-grid mb-0">
          <dt>Gross from repairs</dt><dd class="num">${money(t.grossPaise || 0)}</dd>
          <dt>ReLoop commission</dt><dd class="num">− ${money(t.commissionPaise || 0)}</dd>
          ${t.refundedGrossPaise ? `<dt>Refunded</dt><dd class="num">− ${money(t.refundedGrossPaise)}</dd>` : ''}
          <dt class="fw-semibold">Net earnings</dt><dd class="num fw-semibold">${money(t.currentNetPaise || 0)}</dd>
          <dt>Pending payout</dt><dd class="num">${money(t.pendingPayoutPaise || 0)}</dd>
        </dl><div class="small text-muted mt-2">Payouts are simulated.</div></div></div>`;
    } catch (err) {
      ui.error('#earnings-card', err, earnings, 'Could not load earnings');
    }
  }

  async function stock() {
    try {
      const low = await RL.endpoints.inventory.lowStock();
      if (!low.lowStockAlert) {
        $('#stock-alert').innerHTML = `<div class="card"><div class="card-body d-flex align-items-center gap-2 small"><i class="bi bi-box-seam text-success" aria-hidden="true"></i>Stock levels are fine. <a href="/repairer/inventory.html" class="ms-auto">Inventory</a></div></div>`;
        return;
      }
      $('#stock-alert').innerHTML = `<div class="card border-warning"><div class="card-header d-flex justify-content-between"><span><i class="bi bi-exclamation-triangle text-warning me-1" aria-hidden="true"></i>Low stock</span><a class="small" href="/repairer/inventory.html">Manage</a></div>
        <ul class="list-group list-group-flush">${low.items.slice(0, 5).map((p) => `<li class="list-group-item d-flex justify-content-between"><span>${esc(p.partName)} <span class="small text-muted mono">${esc(p.sku || '')}</span></span>${badge(p.stockStatus, `${p.quantity} left`)}</li>`).join('')}</ul></div>`;
    } catch (err) {
      ui.error('#stock-alert', err, stock, 'Could not load stock levels');
    }
  }

  // Live refresh: a new request, a payment (confirmed or failed) or a refund changes this snapshot; the request
  // and earnings sections are then redrawn quietly (they both write into #stats, so it is cleared first).
  const snapshot = () => Promise.all([
    RL.endpoints.repairRequests.incoming({ status: NEW, size: 5 }),
    RL.endpoints.repairRequests.incoming({ status: ACTIVE, size: 50 }),
    RL.endpoints.reports.providerEarnings(account.id),
  ]);
  const live = ui.poll(snapshot, () => { $('#stats').innerHTML = ''; requests(true); earnings(true); stock(); }, { interval: 15000 });

  listing();
  requests();
  earnings();
  stock();
  snapshot().then((s) => live.mark(s), () => {});
});
