/*
 * Repairer job lists (requests / active / completed): GET /api/repair-requests/incoming?status=...
 * The bucket comes from the page (#bucket[data-bucket]); each bucket has its own filter tabs.
 */
RL.page(async () => {
  const { $, $$, esc, rupees, label, badge, dateTime, relative } = RL.ui;
  const ui = RL.ui;
  const BUCKETS = {
    requests: {
      empty: { icon: 'bi-inbox', title: 'No new requests', text: 'When a customer asks your workshop for a repair, it appears here for you to accept or reject.' },
      tabs: [{ id: 'new', label: 'Waiting for you', status: ['REQUESTED'] }],
    },
    active: {
      empty: { icon: 'bi-wrench-adjustable', title: 'No active repairs', text: 'Accepted requests appear here until they are completed.', actionHref: '/repairer/requests.html', actionLabel: 'View new requests' },
      tabs: [
        { id: 'all', label: 'All active', status: ['ACCEPTED', 'INSPECTED', 'QUOTED', 'QUOTE_APPROVED', 'AWAITING_PAYMENT', 'PAID', 'IN_PROGRESS'] },
        { id: 'quote', label: 'To inspect or quote', status: ['ACCEPTED', 'INSPECTED', 'QUOTE_REJECTED'] },
        { id: 'customer', label: 'Waiting for customer', status: ['QUOTED', 'QUOTE_APPROVED', 'AWAITING_PAYMENT'] },
        { id: 'work', label: 'Ready to repair', status: ['PAID', 'IN_PROGRESS'] },
      ],
    },
    completed: {
      empty: { icon: 'bi-check2-square', title: 'Nothing here yet', text: 'Completed, rejected and cancelled jobs are kept here.' },
      tabs: [
        { id: 'completed', label: 'Completed', status: ['COMPLETED'] },
        { id: 'closed', label: 'Rejected & cancelled', status: ['REJECTED', 'QUOTE_REJECTED', 'CANCELLED'] },
      ],
    },
  };
  const bucket = BUCKETS[$('#bucket').dataset.bucket];
  let tab = bucket.tabs.find((t) => t.id === ui.param('filter')) || bucket.tabs[0];
  let page = 0;
  const NEXT = { REQUESTED: 'Accept or reject', ACCEPTED: 'Inspect', INSPECTED: 'Send quote', QUOTE_REJECTED: 'Revise quote', PAID: 'Start repair', IN_PROGRESS: 'Update or complete' };

  function tabs() {
    if (bucket.tabs.length < 2) { $('#filter-tabs').remove(); return; }
    $('#filter-tabs').innerHTML = bucket.tabs.map((t) => `<li class="nav-item" role="presentation"><button type="button" role="tab" class="nav-link${t === tab ? ' active' : ''}" aria-selected="${t === tab}" data-tab="${t.id}">${esc(t.label)}</button></li>`).join('');
    $$('[data-tab]').forEach((b) => b.addEventListener('click', () => { tab = bucket.tabs.find((t) => t.id === b.dataset.tab); page = 0; tabs(); load(); }));
  }

  async function load() {
    ui.loader('#jobs', 'Loading repair jobs…');
    $('#pager').innerHTML = '';
    try {
      const res = await RL.endpoints.repairRequests.incoming({ status: tab.status, page, size: 20 });
      const href = (r) => '/repairer/repair-request.html?id=' + encodeURIComponent(r.id);
      ui.table('#jobs', [
        { label: 'Request', render: (r) => `<a class="fw-semibold mono" href="${href(r)}">${esc(r.requestNumber)}</a><div class="small text-muted">${esc(dateTime(r.createdAt))}</div>` },
        { label: 'Item', render: (r) => `${esc(r.itemDescription)}<div class="small text-muted">${esc(label(r.category))}</div>` },
        { label: 'Hand-over', render: (r) => `<span class="small">${esc(label(r.deliveryMethod))}</span>` },
        { label: 'Quote total', className: 'num', render: (r) => r.quoteTotalPayable !== null && r.quoteTotalPayable !== undefined ? rupees(r.quoteTotalPayable) : '<span class="text-muted small">—</span>' },
        { label: 'Status', render: (r) => badge(r.status) },
        { label: 'Updated', render: (r) => `<span class="small">${esc(relative(r.updatedAt || r.createdAt))}</span>` },
        { label: '', className: 'text-end', render: (r) => `<a class="btn btn-sm ${NEXT[r.status] ? 'btn-primary' : 'btn-light border'}" href="${href(r)}">${esc(NEXT[r.status] || 'Open')}</a>` },
      ], res.content, bucket.empty);
      ui.pagination('#pager', res, (n) => { page = n; load(); });
    } catch (err) {
      ui.error('#jobs', err, load, 'Could not load repair jobs');
    }
  }
  tabs();
  load();
});
