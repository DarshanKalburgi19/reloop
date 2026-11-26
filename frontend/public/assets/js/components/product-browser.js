/*
 * Product browser (GET /api/products): search box, filters, sort, paging and a card grid. Used by the public
 * marketplace and by the customer dashboard's "Buy products" page. Filters are mirrored into the URL so results are
 * shareable and survive a refresh.
 *
 *   RL.productBrowser.mount({filters: el, results: el, pager: el, count: el, size})
 */
(function (window) {
  'use strict';

  const RL = window.RL = window.RL || {};
  const { esc, options, ENUMS, $, $$ } = RL.ui;

  const KEYS = ['q', 'category', 'condition', 'city', 'minPrice', 'maxPrice', 'sort'];

  function filtersHtml(state) {
    return `<form class="card" id="pb-form" role="search" aria-label="Filter products"><div class="card-body d-grid gap-3">
      <div><label class="form-label" for="pb-q">Search</label>
        <div class="input-group"><span class="input-group-text bg-white"><i class="bi bi-search" aria-hidden="true"></i></span>
        <input class="form-control" id="pb-q" name="q" value="${esc(state.q || '')}" placeholder="Sofa, iPhone, cycle…" maxlength="100"></div></div>
      <div><label class="form-label" for="pb-category">Category</label>
        <select class="form-select" id="pb-category" name="category">${options(ENUMS.productCategory, state.category, 'All categories')}</select></div>
      <div><label class="form-label" for="pb-condition">Condition</label>
        <select class="form-select" id="pb-condition" name="condition">${options(ENUMS.productCondition, state.condition, 'Any condition')}</select></div>
      <div><label class="form-label" for="pb-city">City</label>
        <input class="form-control" id="pb-city" name="city" value="${esc(state.city || '')}" placeholder="Ahmedabad" maxlength="120"></div>
      <div><span class="form-label d-block">Price (₹)</span><div class="d-flex gap-2">
        <input class="form-control" name="minPrice" type="number" min="0" step="1" value="${esc(state.minPrice || '')}" placeholder="Min" aria-label="Minimum price">
        <input class="form-control" name="maxPrice" type="number" min="0" step="1" value="${esc(state.maxPrice || '')}" placeholder="Max" aria-label="Maximum price"></div></div>
      <div class="d-flex gap-2"><button class="btn btn-primary flex-grow-1" type="submit">Apply filters</button>
        <button class="btn btn-light border" type="reset">Clear</button></div>
    </div></form>`;
  }

  function mount(o) {
    const params = new URLSearchParams(window.location.search);
    const state = { page: Math.max(0, Number(params.get('page')) || 0) };
    KEYS.forEach((k) => { if (params.get(k)) state[k] = params.get(k); });
    if (!state.sort) state.sort = 'newest';

    o.filters.innerHTML = filtersHtml(state);
    const form = $('#pb-form', o.filters);
    if (o.sortSelect) {
      o.sortSelect.innerHTML = options([
        { value: 'newest', label: 'Newest first' }, { value: 'oldest', label: 'Oldest first' },
        { value: 'price_asc', label: 'Price: low to high' }, { value: 'price_desc', label: 'Price: high to low' },
      ], state.sort);
      o.sortSelect.addEventListener('change', () => { state.sort = o.sortSelect.value; state.page = 0; load(); });
    }

    function syncUrl() {
      const p = new URLSearchParams();
      KEYS.forEach((k) => { if (state[k]) p.set(k, state[k]); });
      if (state.page) p.set('page', state.page);
      const qs = p.toString();
      window.history.replaceState(null, '', window.location.pathname + (qs ? '?' + qs : ''));
    }

    async function load() {
      syncUrl();
      RL.ui.loader(o.results, 'Finding products…');
      o.pager.innerHTML = '';
      if (o.count) o.count.textContent = '';
      try {
        const query = { page: state.page, size: o.size || 12 };
        KEYS.forEach((k) => { if (state[k]) query[k] = state[k]; });
        const page = await RL.endpoints.products.search(query);
        if (o.count) o.count.textContent = `${RL.ui.number(page.totalElements)} product${page.totalElements === 1 ? '' : 's'}`;
        if (!page.content.length) {
          const filtered = KEYS.some((k) => k !== 'sort' && state[k]);
          RL.ui.empty(o.results, filtered
            ? { icon: 'bi-funnel', title: 'No products match these filters', text: 'Try a different category, city or price range.' }
            : { icon: 'bi-bag', title: 'No products listed yet', text: 'Check back soon, or list something of your own.', actionHref: '/customer/sell.html', actionLabel: 'Sell an item' });
          return;
        }
        o.results.innerHTML = `<div class="row g-3">${page.content.map((p) => `<div class="col-6 col-md-4 ${o.wide ? 'col-xl-3' : ''}">${RL.cards.productCard(p)}</div>`).join('')}</div>`;
        RL.ui.pagination(o.pager, page, (n) => { state.page = n; load(); window.scrollTo({ top: 0 }); });
      } catch (err) {
        RL.ui.error(o.results, err, load, 'Could not load products');
      }
    }

    form.addEventListener('submit', (ev) => {
      ev.preventDefault();
      const v = RL.ui.formValues(form);
      KEYS.filter((k) => k !== 'sort').forEach((k) => { state[k] = v[k] || ''; });
      if (state.minPrice && state.maxPrice && Number(state.minPrice) > Number(state.maxPrice)) {
        RL.ui.toast('The minimum price is higher than the maximum price.', 'warning');
        return;
      }
      state.page = 0;
      load();
    });
    form.addEventListener('reset', () => {
      window.setTimeout(() => {
        $$('input, select', form).forEach((el) => { el.value = ''; });
        KEYS.filter((k) => k !== 'sort').forEach((k) => { state[k] = ''; });
        state.page = 0;
        load();
      }, 0);
    });

    load();
    return { reload: load };
  }

  RL.productBrowser = { mount };
})(window);
