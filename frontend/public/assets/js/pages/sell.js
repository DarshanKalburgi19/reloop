/*
 * Sell / edit a listing.
 *   create: POST /api/products            edit (?id=): GET + PUT /api/products/{id}
 * The right-hand panel shows, live, what the seller would earn at the entered price using the published
 * MARKETPLACE_PURCHASE rates (GET /api/fee-rates): price, buyer's fee, buyer's total, gross, commission, net and
 * ReLoop's total commission.
 */
RL.page(async ({ account }) => {
  const { $, $$, esc, options, ENUMS, label } = RL.ui;
  const ui = RL.ui;
  const editId = ui.param('id');
  const host = $('#sell-form-host');
  const PLACEHOLDERS = ['electronics', 'furniture', 'fashion', 'books', 'home-kitchen', 'sports-toys', 'appliances', 'other']
    .map((s) => '/demo-images/placeholders/' + s + '.svg');

  let product = null;
  if (editId) {
    $('#sell-title').textContent = 'Edit listing';
    document.title = 'Edit listing — ReLoop';
    ui.loader(host, 'Loading your listing…');
    try {
      product = await RL.endpoints.products.get(editId);
    } catch (err) {
      ui.error(host, err, () => window.location.reload(), 'Could not load this listing');
      return;
    }
    if (product.sellerId !== account.id) {
      ui.error(host, new Error('You can only edit your own listings.'), null, 'Not your listing');
      return;
    }
  }

  const p = product || {};
  const customerCity = account.customer && account.customer.city;
  const imgs = (p.images || []).map((i) => i.url);
  const extraUrls = imgs.filter((u) => !PLACEHOLDERS.includes(u));
  const statusOptions = product && ['ACTIVE', 'DRAFT'].includes(product.availability) ? ['ACTIVE', 'DRAFT'] : null;

  host.innerHTML = `<form class="card" id="sell-form" novalidate><div class="card-body">
    <div class="form-section">
      <h2 class="form-section-title">What are you selling?</h2>
      <div class="row g-3 mt-1">
        <div class="col-12"><label class="form-label required" for="title">Title</label>
          <input class="form-control" id="title" name="title" required minlength="3" maxlength="200" value="${esc(p.title || '')}" placeholder="e.g. Teak wood study table">
          <div class="invalid-feedback">3–200 characters.</div></div>
        <div class="col-md-6"><label class="form-label required" for="category">Category</label>
          <select class="form-select" id="category" name="category" required>${options(ENUMS.productCategory, p.category, 'Choose a category')}</select>
          <div class="invalid-feedback">Choose a category.</div></div>
        <div class="col-md-6"><label class="form-label required" for="condition">Condition</label>
          <select class="form-select" id="condition" name="condition" required>${options(ENUMS.productCondition, p.condition, 'Choose the condition')}</select>
          <div class="invalid-feedback">Choose the condition.</div></div>
        <div class="col-12"><label class="form-label required" for="description">Description</label>
          <textarea class="form-control" id="description" name="description" rows="5" required maxlength="4000" placeholder="Age, usage, any defects, what's included…">${esc(p.description || '')}</textarea>
          <div class="invalid-feedback">Describe the item.</div></div>
      </div>
    </div>
    <div class="form-section">
      <h2 class="form-section-title">Price and pickup</h2>
      <div class="row g-3 mt-1">
        <div class="col-md-4"><label class="form-label required" for="price">Price (₹)</label>
          <input class="form-control" id="price" name="price" type="number" required min="1" max="10000000" step="0.01" value="${esc(p.price || '')}">
          <div class="invalid-feedback">Between ₹1 and ₹1,00,00,000.</div></div>
        <div class="col-md-4"><label class="form-label required" for="city">City</label>
          <input class="form-control" id="city" name="city" required maxlength="120" value="${esc(p.city || customerCity || '')}">
          <div class="invalid-feedback">Enter the city.</div></div>
        <div class="col-md-4"><label class="form-label" for="pickupLocation">Pickup area</label>
          <input class="form-control" id="pickupLocation" name="pickupLocation" maxlength="255" value="${esc(p.pickupLocation || '')}" placeholder="e.g. Navrangpura"></div>
        ${statusOptions ? `<div class="col-md-4"><label class="form-label" for="status">Status</label>
          <select class="form-select" id="status" name="status">${options(statusOptions.map((s) => ({ value: s, label: s === 'ACTIVE' ? 'Active (visible)' : 'Draft (hidden)' })), product.availability)}</select></div>` : ''}
        ${product && !statusOptions ? `<div class="col-12"><div class="alert alert-info small mb-0">This listing is ${esc(label(product.availability))}; its status can no longer be changed here.</div></div>` : ''}
        ${!product ? `<div class="col-md-8"><label class="form-label" for="passportCode">Product passport code <span class="text-muted fw-normal">(optional)</span></label>
          <input class="form-control" id="passportCode" name="passportCode" maxlength="24" placeholder="If this item already has a ReLoop passport">
          <div class="form-text">Re-listing something bought on ReLoop? Enter its passport code to keep its history.</div></div>` : ''}
      </div>
    </div>
    <div class="form-section">
      <h2 class="form-section-title">Photos</h2>
      <p class="small text-muted mb-2">Choose up to 8 images. The first selected image is the cover.</p>
      <div class="image-pick mb-3" data-field="imageUrls">${PLACEHOLDERS.map((u, i) => `<label title="${esc(label(u.split('/').pop().replace('.svg', '').replace('-', '_').toUpperCase()))}"><input type="checkbox" name="placeholder" value="${esc(u)}" ${imgs.includes(u) ? 'checked' : ''} aria-label="Demo image ${i + 1}"><img src="${esc(u)}" alt=""></label>`).join('')}</div>
      <label class="form-label" for="extraUrls">Image links <span class="text-muted fw-normal">(optional, one https:// link per line)</span></label>
      <textarea class="form-control mono" id="extraUrls" name="extraUrls" rows="2" placeholder="https://example.com/photo.jpg">${esc(extraUrls.join('\n'))}</textarea>
    </div>
    <div class="form-section d-flex flex-wrap gap-2">
      <button class="btn btn-primary btn-lg" type="submit">${product ? 'Save changes' : 'Publish listing'}</button>
      <a class="btn btn-light border btn-lg" href="/customer/listings.html">Cancel</a>
    </div>
  </div></form>`;

  const form = $('#sell-form');
  let rate = null;

  function preview() {
    const target = $('#earnings-preview');
    if (!rate) return;
    const price = Number($('#price').value);
    if (!price || price < 1) {
      target.innerHTML = `<div class="card"><div class="card-body small text-muted"><i class="bi bi-calculator me-1"></i>Enter a price to see the full breakdown: what the buyer pays, ReLoop's fees and your net earnings.</div></div>`;
      return;
    }
    RL.fees.render(target, RL.fees.estimate(Math.round(price * 100), rate, 'MARKETPLACE_PURCHASE'),
      { title: 'If it sells at this price', providerLabel: 'Seller' });
  }

  try {
    const rates = await RL.fees.rates();
    rate = rates.MARKETPLACE_PURCHASE || null;
    if (!rate) $('#earnings-preview').innerHTML = '<div class="alert alert-warning small">Marketplace fee rates are not published right now, so the earnings preview is unavailable. The exact breakdown is always recorded on each order.</div>';
    preview();
  } catch (err) {
    ui.error('#earnings-preview', err, null, 'Could not load fee rates for the earnings preview');
  }
  $('#price').addEventListener('input', preview);

  // Auto-select the category placeholder for a new listing if the seller has not picked any image yet.
  $('#category').addEventListener('change', () => {
    if (product) return;
    const chosen = $$('input[name="placeholder"]:checked', form);
    if (chosen.length) return;
    const slug = String($('#category').value || '').toLowerCase().replace(/_/g, '-');
    const box = $$('input[name="placeholder"]', form).find((i) => i.value.endsWith('/' + slug + '.svg'));
    if (box) box.checked = true;
  });

  form.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    ui.clearErrors(form);
    if (!ui.validate(form)) return;
    const v = ui.formValues(form);
    const urls = ui.checkedValues(form, 'placeholder')
      .concat(String(v.extraUrls || '').split(/\s+/).map((s) => s.trim()).filter(Boolean));
    const bad = urls.filter((u) => !/^https:\/\/\S+$/.test(u) && !u.startsWith('/demo-images/'));
    if (bad.length) { ui.showErrors(form, { message: 'Image links must start with https://', fieldErrors: { extraUrls: 'invalid link: ' + bad[0] } }); return; }
    if (urls.length > 8) { ui.showErrors(form, { message: 'Too many images.', fieldErrors: { imageUrls: 'choose at most 8 images' } }); return; }
    const body = {
      title: v.title, description: v.description, category: v.category, condition: v.condition,
      price: Number(v.price), city: v.city, pickupLocation: v.pickupLocation || null, imageUrls: urls,
    };
    try {
      let saved;
      if (product) {
        if (v.status) body.status = v.status;
        saved = await ui.busy(form.querySelector('[type=submit]'), () => RL.endpoints.products.update(product.id, body), 'Saving…');
        ui.flash(`"${saved.title}" was updated.`, 'success');
      } else {
        if (v.passportCode) body.passportCode = v.passportCode;
        saved = await ui.busy(form.querySelector('[type=submit]'), () => RL.endpoints.products.create(body), 'Publishing…');
        ui.flash(`"${saved.title}" is now listed.`, 'success');
      }
      window.location.href = '/customer/listings.html';
    } catch (err) {
      ui.showErrors(form, err);
    }
  });
});
