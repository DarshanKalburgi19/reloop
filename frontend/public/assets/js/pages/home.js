/*
 * Homepage: live fee rates (GET /api/fee-rates), newest products (GET /api/products) and top-rated repairers
 * (GET /api/repairers). Each section loads independently and shows its own error state if its service is down.
 */
RL.page(async ({ account }) => {
  const { $, esc, money, percent, error, empty, loader } = RL.ui;

  if (account) {
    const home = RL.layout.HOME[account.role] || '/';
    $('#hero-actions').insertAdjacentHTML('afterbegin',
      `<a class="btn btn-soft btn-lg" href="${home}"><i class="bi bi-grid-1x2 me-1" aria-hidden="true"></i>Go to your dashboard</a>`);
  }

  async function loadFees() {
    loader('#fee-rates-table', 'Loading current rates…');
    try {
      const rates = await RL.fees.rates();
      const m = rates.MARKETPLACE_PURCHASE;
      const r = rates.REPAIR_BOOKING;
      $('#fee-rates-table').innerHTML = RL.fees.ratesTableHtml(rates);
      if (m) {
        const e = RL.fees.estimate(100000, m, 'MARKETPLACE_PURCHASE');
        $('#hero-fees').innerHTML = `
          <div class="hero-stat d-flex justify-content-between"><span>You pay</span><strong>${money(e.total)}</strong></div>
          <div class="small text-white-50">₹1,000 item + ${esc(percent(m.customerFeeRate))} customer platform fee (${money(e.customerFee)})</div>
          <div class="hero-stat d-flex justify-content-between"><span>Seller receives</span><strong>${money(e.providerNet)}</strong></div>
          <div class="small text-white-50">₹1,000 − ${esc(percent(m.providerCommissionRate))} seller commission (${money(e.providerCommission)})</div>
          <div class="hero-stat d-flex justify-content-between"><span>ReLoop keeps</span><strong>${money(e.platformTotal)}</strong></div>
          <div class="small text-white-50">Live rates from ReLoop's fee configuration.</div>`;
        RL.fees.render('#fee-example-market', e, { title: 'Buying a ₹1,000 product', note: 'Example only.' });
      } else {
        $('#hero-fees').innerHTML = '<p class="mb-0 text-white-50">Marketplace rates are not published right now.</p>';
      }
      if (r) {
        RL.fees.render('#fee-example-repair', RL.fees.estimate(250000, r, 'REPAIR_BOOKING'), { title: 'Approving a ₹2,500 repair quote', note: 'Example only.' });
      }
    } catch (err) {
      $('#hero-fees').innerHTML = `<p class="mb-0 text-white-50"><i class="bi bi-exclamation-triangle me-1"></i>Fee rates are unavailable right now (${esc(err.message)}). They are always shown in full at checkout.</p>`;
      error('#fee-rates-table', err, loadFees, 'Could not load the current fee rates');
      $('#fee-example-market').innerHTML = '';
      $('#fee-example-repair').innerHTML = '';
    }
  }

  async function loadProducts() {
    loader('#home-products', 'Loading products…');
    try {
      const page = await RL.endpoints.products.search({ sort: 'newest', size: 4 });
      if (!page.content.length) {
        empty('#home-products', { icon: 'bi-bag', title: 'No products listed yet', text: 'Be the first to give something a second life.', actionHref: '/customer/sell.html', actionLabel: 'List an item' });
        return;
      }
      $('#home-products').innerHTML = `<div class="row g-3">${page.content.map((p) => `<div class="col-6 col-lg-3">${RL.cards.productCard(p)}</div>`).join('')}</div>`;
    } catch (err) {
      error('#home-products', err, loadProducts, 'Could not load products');
    }
  }

  async function loadRepairers() {
    loader('#home-repairers', 'Loading repairers…');
    try {
      const page = await RL.endpoints.repairers.search({ sort: 'rating', size: 3 });
      if (!page.content.length) {
        empty('#home-repairers', { icon: 'bi-tools', title: 'No workshops listed yet', text: 'Repair workshops appear here once their profile is complete.', actionHref: '/register-repairer.html', actionLabel: 'List your workshop' });
        return;
      }
      $('#home-repairers').innerHTML = `<div class="row g-3">${page.content.map((r) => `<div class="col-md-4">${RL.cards.repairerCard(r)}</div>`).join('')}</div>`;
    } catch (err) {
      error('#home-repairers', err, loadRepairers, 'Could not load repairers');
    }
  }

  await Promise.all([loadFees(), loadProducts(), loadRepairers()]);
});
