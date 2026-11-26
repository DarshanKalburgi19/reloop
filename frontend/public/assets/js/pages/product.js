/*
 * Product details: GET /api/products/{id}, GET /api/products/{id}/passport, GET /api/fee-rates.
 * "Buy now" creates (or reuses) a pending order with POST /api/orders and goes to checkout. The fee breakdown here is an
 * estimate from the published rates; checkout shows the exact amounts fixed on the order.
 */
RL.page(async ({ account }) => {
  const { $, $$, esc, safeUrl, money, label, badge, date, dateTime } = RL.ui;
  const ui = RL.ui;
  const id = ui.param('id');
  const host = $('#product');
  if (!id || !/^\d+$/.test(id)) {
    host.innerHTML = `<div class="card">${ui.emptyHtml({ icon: 'bi-question-circle', title: 'No product selected', text: 'Open a product from the marketplace.', actionHref: '/marketplace.html', actionLabel: 'Browse the marketplace' })}</div>`;
    return;
  }

  async function load() {
    ui.loader(host, 'Loading product…');
    let p;
    try {
      p = await RL.endpoints.products.get(id);
    } catch (err) {
      if (err.status === 404) {
        host.innerHTML = `<div class="card">${ui.emptyHtml({ icon: 'bi-bag-x', title: 'Product not found', text: 'It may have been removed by the seller.', actionHref: '/marketplace.html', actionLabel: 'Browse the marketplace' })}</div>`;
        return;
      }
      ui.error(host, err, load, 'Could not load this product');
      return;
    }
    document.title = `${p.title} — ReLoop marketplace`;
    $('#crumb').textContent = p.title;
    const images = (p.images || []).map((i) => ({ url: safeUrl(i.url), alt: i.altText || p.title, placeholder: i.placeholder })).filter((i) => i.url);
    if (!images.length) images.push({ url: RL.cards.placeholderFor(p.category), alt: p.title, placeholder: true });
    const own = account && account.id === p.sellerId;
    const fallback = RL.cards.placeholderFor(p.category);

    host.innerHTML = `<div class="row g-4 g-lg-5">
      <div class="col-lg-7">
        <div class="card overflow-hidden"><div class="ratio ratio-4x3 bg-light"><img id="main-img" src="${esc(images[0].url)}" alt="${esc(images[0].alt)}" class="object-fit-contain" data-fallback="${esc(fallback)}"></div></div>
        ${images.length > 1 ? `<div class="d-flex flex-wrap gap-2 mt-2" role="list" aria-label="Product photos">${images.map((im, i) => `<button type="button" class="btn p-0 border rounded overflow-hidden${i === 0 ? ' border-primary' : ''}" data-thumb="${i}" aria-label="Show photo ${i + 1}" role="listitem"><img src="${esc(im.url)}" alt="" width="72" height="72" class="object-fit-cover" data-fallback="${esc(fallback)}"></button>`).join('')}</div>` : ''}
        ${images.every((im) => im.placeholder) ? '<div class="small text-muted mt-2"><i class="bi bi-image me-1" aria-hidden="true"></i>Illustration only. The seller has not added photos.</div>' : ''}
        <div class="card mt-4"><div class="card-header">Description</div><div class="card-body"><p class="mb-0 text-pre-wrap">${esc(p.description || 'No description provided.')}</p></div></div>
        <div id="passport" class="mt-4"></div>
      </div>
      <div class="col-lg-5">
        <div class="card"><div class="card-body d-grid gap-3">
          <div class="d-flex flex-wrap gap-2"><span class="chip">${esc(label(p.category))}</span><span class="chip">${esc(label(p.condition))}</span>${p.availability !== 'ACTIVE' ? badge(p.availability) : ''}</div>
          <h1 class="h3 mb-0">${esc(p.title)}</h1>
          <div class="display-6 fw-semibold">${money(p.pricePaise)}</div>
          <dl class="dl-grid small mb-0">
            <dt>Seller</dt><dd>${esc(p.sellerName || 'ReLoop seller')}</dd>
            <dt>Location</dt><dd>${esc(p.city)}${p.pickupLocation ? ` · ${esc(p.pickupLocation)}` : ''}</dd>
            ${p.store ? `<dt>Store</dt><dd>${esc(p.store.name)}</dd>` : ''}
            <dt>Listed</dt><dd>${esc(date(p.createdAt))}</dd>
          </dl>
          <div id="buy-box"></div>
        </div></div>
        <div id="fees" class="mt-4"></div>
      </div>
    </div>`;

    $$('[data-thumb]', host).forEach((b) => b.addEventListener('click', () => {
      const im = images[Number(b.dataset.thumb)];
      const main = $('#main-img');
      main.dataset.fellBack = '';
      main.src = im.url;
      main.alt = im.alt;
      $$('[data-thumb]', host).forEach((x) => x.classList.toggle('border-primary', x === b));
    }));

    buyBox(p, own);
    fees(p);
    if (p.passportCode) passport(p);
  }

  function buyBox(p, own) {
    const box = $('#buy-box');
    if (own) {
      box.innerHTML = `<div class="alert alert-info small mb-0">This is your listing. <a href="/customer/listings.html">Manage your listings</a></div>`;
      return;
    }
    if (!p.available) {
      box.innerHTML = `<div class="alert alert-secondary small mb-0">This item is ${esc(label(p.availability).toLowerCase())} and can't be bought right now.</div>`;
      return;
    }
    if (account && account.role !== 'CUSTOMER') {
      box.innerHTML = `<div class="alert alert-secondary small mb-0">Buying is available with a customer account. You are signed in as a repairer.</div>`;
      return;
    }
    box.innerHTML = `<button type="button" class="btn btn-primary btn-lg w-100" id="buy"><i class="bi bi-bag-check me-1" aria-hidden="true"></i>Buy now</button>
      <div class="small text-muted text-center mt-2">${account ? 'You will review the exact total before paying.' : 'You will be asked to sign in first.'}</div>`;
    $('#buy').addEventListener('click', async (ev) => {
      if (!account) {
        window.location.href = RL.layout.loginUrl(window.location.pathname + window.location.search);
        return;
      }
      try {
        const order = await ui.busy(ev.currentTarget, () => RL.endpoints.orders.create(p.id), 'Reserving…');
        window.location.href = '/checkout.html?orderId=' + encodeURIComponent(order.id);
      } catch (err) {
        ui.toastError(err, 'Could not start the order');
        if (err.status === 409) load();
      }
    });
  }

  async function fees(p) {
    try {
      const rates = await RL.fees.rates();
      const r = rates.MARKETPLACE_PURCHASE;
      if (!r) return;
      RL.fees.render('#fees', RL.fees.estimate(p.pricePaise, r, 'MARKETPLACE_PURCHASE'), { title: 'What you pay' });
    } catch (err) {
      ui.error('#fees', err, () => fees(p), 'Could not load the current fee rates');
    }
  }

  async function passport(p) {
    try {
      const pp = await RL.endpoints.products.passport(p.id);
      $('#passport').innerHTML = `<div class="card"><div class="card-header d-flex justify-content-between align-items-center"><span><i class="bi bi-journal-check me-1" aria-hidden="true"></i>Product passport</span><span class="mono small">${esc(pp.passportCode)}</span></div>
        <div class="card-body"><p class="small text-muted">A record of this item's life on ReLoop: ${pp.listingCount} listing(s), ${pp.ownershipTransfers} ownership transfer(s) since ${esc(date(pp.issuedAt))}.</p>
        <ul class="timeline mb-0">${(pp.history || []).map((h) => `<li><div class="tl-title">${esc(label(h.eventType))}${h.price !== null && h.price !== undefined ? ` · ${esc(RL.ui.rupees(h.price))}` : ''}</div><div class="tl-meta">${esc(dateTime(h.occurredAt))}${h.details ? ' · ' + esc(h.details) : ''}</div></li>`).join('')}</ul></div></div>`;
    } catch (err) {
      $('#passport').innerHTML = '';
    }
  }

  load();
});
