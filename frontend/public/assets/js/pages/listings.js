/*
 * My listings: GET /api/products/mine (paged). Edit -> sell.html?id=, remove -> DELETE /api/products/{id}.
 * "You receive" uses the published marketplace rates so sellers always see their net, not just the price.
 */
RL.page(async () => {
  const { $, $$, esc, rupees, money, label, badge, date } = RL.ui;
  const ui = RL.ui;
  let page = 0;
  let rate = null;
  RL.fees.rates().then((r) => { rate = r.MARKETPLACE_PURCHASE || null; }).catch(() => { rate = null; }).finally(load);

  async function load() {
    ui.loader('#listings', 'Loading your listings…');
    $('#pager').innerHTML = '';
    try {
      const res = await RL.endpoints.products.mine({ page, size: 20 });
      ui.table('#listings', [
        { label: 'Item', render: (p) => `<div class="d-flex align-items-center gap-3"><img src="${esc(RL.cards.productImage(p))}" data-fallback="${esc(RL.cards.placeholderFor(p.category))}" alt="" width="56" height="42" class="rounded border object-fit-cover bg-light">
          <div class="min-w-0"><a class="fw-semibold" href="/product.html?id=${encodeURIComponent(p.id)}">${esc(p.title)}</a><div class="small text-muted">${esc(label(p.category))} · ${esc(label(p.condition))} · ${esc(p.city)}</div></div></div>` },
        { label: 'Price', className: 'num', render: (p) => rupees(p.price) },
        { label: 'You receive', className: 'num', render: (p) => rate ? `${money(RL.fees.estimate(p.pricePaise, rate).providerNet)}<div class="small text-muted">after ${esc(RL.ui.percent(rate.providerCommissionRate))} commission</div>` : '<span class="text-muted small">—</span>' },
        { label: 'Status', render: (p) => badge(p.availability) },
        { label: 'Listed', render: (p) => esc(date(p.createdAt)) },
        { label: '', className: 'text-end text-nowrap', render: (p) => ['ACTIVE', 'DRAFT'].includes(p.availability)
          ? `<a class="btn btn-light btn-sm border" href="/customer/sell.html?id=${encodeURIComponent(p.id)}"><i class="bi bi-pencil"></i> Edit</a>
             <button class="btn btn-outline-danger btn-sm" type="button" data-remove="${esc(p.id)}" data-title="${esc(p.title)}"><i class="bi bi-trash"></i> Remove</button>`
          : '<span class="small text-muted">No actions</span>' },
      ], res.content, { icon: 'bi-tags', title: 'You have no listings yet', text: 'List something you no longer use and give it a second life.', actionHref: '/customer/sell.html', actionLabel: 'Sell an item' });
      ui.pagination('#pager', res, (n) => { page = n; load(); });
      $$('[data-remove]').forEach((b) => b.addEventListener('click', () => remove(b)));
    } catch (err) {
      ui.error('#listings', err, load, 'Could not load your listings');
    }
  }

  async function remove(btn) {
    const ok = await ui.confirm({ title: 'Remove listing?', message: `"${btn.dataset.title}" will be taken off the marketplace.`, confirmLabel: 'Remove listing', confirmClass: 'btn-danger' });
    if (!ok) return;
    try {
      await ui.busy(btn, () => RL.endpoints.products.deactivate(btn.dataset.remove), 'Removing…');
      ui.toast('Listing removed from the marketplace.', 'success');
      load();
    } catch (err) {
      ui.toastError(err, 'Could not remove the listing');
    }
  }
});
