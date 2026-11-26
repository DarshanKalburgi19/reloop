/* My repair requests: GET /api/repair-requests/mine?status=...&page= with filter tabs. */
RL.page(async () => {
  const { $, $$, esc, rupees, label, badge, dateTime, relative } = RL.ui;
  const ui = RL.ui;
  const TABS = [
    { id: 'all', label: 'All', status: null },
    { id: 'action', label: 'Needs your action', status: ['QUOTED', 'AWAITING_PAYMENT'] },
    { id: 'active', label: 'In progress', status: ['REQUESTED', 'ACCEPTED', 'INSPECTED', 'QUOTE_APPROVED', 'PAID', 'IN_PROGRESS'] },
    { id: 'completed', label: 'Completed', status: ['COMPLETED'] },
    { id: 'closed', label: 'Closed', status: ['REJECTED', 'QUOTE_REJECTED', 'CANCELLED'] },
  ];
  let tab = TABS.find((t) => t.id === ui.param('filter')) || TABS[0];
  let page = 0;

  function tabs() {
    $('#filter-tabs').innerHTML = TABS.map((t) => `<li class="nav-item" role="presentation"><button type="button" role="tab" class="nav-link${t === tab ? ' active' : ''}" aria-selected="${t === tab}" data-tab="${t.id}">${esc(t.label)}</button></li>`).join('');
    $$('[data-tab]').forEach((b) => b.addEventListener('click', () => { tab = TABS.find((t) => t.id === b.dataset.tab); page = 0; tabs(); load(); }));
  }

  // the query depends on the tab and page; responses for an older query are dropped
  const query = () => ({ status: tab.status, page, size: 20 });
  const fetchList = async () => { const q = JSON.stringify(query()); return { q, res: await RL.endpoints.repairRequests.mine(query()) }; };
  function draw(res) {
    ui.table('#repairs', [
      { label: 'Request', render: (r) => `<a class="fw-semibold mono" href="/customer/repair-request.html?id=${encodeURIComponent(r.id)}">${esc(r.requestNumber)}</a><div class="small text-muted">${esc(dateTime(r.createdAt))}</div>` },
      { label: 'Item', render: (r) => `${esc(r.itemDescription)}<div class="small text-muted">${esc(label(r.category))} · ${esc(label(r.deliveryMethod))}</div>` },
      { label: 'Workshop', render: (r) => esc(r.repairerBusinessName) },
      { label: 'Quote total', className: 'num', render: (r) => r.quoteTotalPayable !== null && r.quoteTotalPayable !== undefined ? rupees(r.quoteTotalPayable) : '<span class="text-muted small">No quote yet</span>' },
      { label: 'Status', render: (r) => badge(r.status) + ui.paymentBadge(r.paymentStatus) },
      { label: 'Updated', render: (r) => `<span class="small">${esc(relative(r.updatedAt || r.createdAt))}</span>` },
      { label: '', className: 'text-end', render: (r) => `<a class="btn btn-sm ${['QUOTED', 'AWAITING_PAYMENT'].includes(r.status) ? 'btn-primary' : 'btn-light border'}" href="/customer/repair-request.html?id=${encodeURIComponent(r.id)}">${r.status === 'QUOTED' ? 'Review quote' : r.status === 'AWAITING_PAYMENT' ? 'Pay' : 'Open'}</a>` },
    ], res.content, tab.id === 'all'
      ? { icon: 'bi-tools', title: 'No repair requests yet', text: 'Find a workshop near you and describe the problem to get a quote.', actionHref: '/repairers.html', actionLabel: 'Find a repairer' }
      : { icon: 'bi-funnel', title: 'Nothing in this view', text: 'Try another filter.' });
    ui.pagination('#pager', res, (n) => { page = n; load(); });
  }

  async function load() {
    ui.loader('#repairs', 'Loading your repair requests…');
    $('#pager').innerHTML = '';
    try {
      const { res } = await fetchList();
      live.mark({ q: JSON.stringify(query()), res });
      draw(res);
    } catch (err) {
      ui.error('#repairs', err, load, 'Could not load your repair requests');
    }
  }
  tabs();
  // quotes, payment results and progress from the workshop appear without a reload
  const live = ui.poll(fetchList, ({ q, res }) => { if (q === JSON.stringify(query())) draw(res); });
  load();
});
