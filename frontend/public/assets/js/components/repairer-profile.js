/*
 * Repairer business-profile editor (repair-service GET /api/repairers/me + PUT /api/repairers/{id}).
 *
 * PUT replaces the whole profile, so every page edits its own section and the editor merges it with the rest of the
 * current profile before saving. A new repairer's profile (created at registration) is incomplete; because the API
 * needs description, categories, services, hours, PIN code, location and methods on every save, the editor also
 * shows any *other* section that is still missing, with a note explaining why.
 *
 *   RL.repairerProfile.mountEditor(container, ['services'] | ['location', 'hours'] | ['business'], {onSaved})
 *   RL.repairerProfile.me()            cached GET /api/repairers/me (null when 404)
 *   RL.repairerProfile.SECTION_OF      missingForListing key -> section id
 */
(function (window) {
  'use strict';

  const RL = window.RL = window.RL || {};
  const { esc, label, options, ENUMS, $, $$ } = RL.ui;
  const ui = RL.ui;

  const SECTION_OF = { description: 'business', categories: 'services', services: 'services', workingHours: 'hours', pincode: 'location', location: 'location', repairMethods: 'location' };
  const SECTION_TITLE = { business: 'Business details', services: 'Services and prices', location: 'Location and repair methods', hours: 'Opening hours' };
  const MISSING_LABEL = { description: 'workshop description', categories: 'repair categories', services: 'at least one service', workingHours: 'opening hours', pincode: 'PIN code', location: 'map location', repairMethods: 'hand-over methods' };
  const missingText = (list) => (list || []).map((m) => MISSING_LABEL[m] || label(m)).join(', ');
  const SECTION_PAGE = { business: '/repairer/profile.html', services: '/repairer/services.html', location: '/repairer/location.html', hours: '/repairer/location.html' };

  let mePromise = null;
  function me(force) {
    if (!mePromise || force) {
      mePromise = RL.endpoints.repairers.me().catch((err) => {
        mePromise = null;
        if (err.status === 404) return null;
        throw err;
      });
    }
    return mePromise;
  }

  function toRequest(d) {
    return {
      businessName: d.businessName || '',
      description: d.description || '',
      categories: d.categories || [],
      expertise: d.expertise || [],
      services: (d.services || []).map((s) => ({ id: s.id, name: s.name, description: s.description || null, category: s.category, priceType: s.priceType, price: Number(s.price), estimatedDays: s.estimatedDays || null })),
      phone: d.phone || null,
      addressLine: d.addressLine || '',
      city: d.city || '',
      pincode: d.pincode || '',
      latitude: d.latitude === undefined ? null : d.latitude,
      longitude: d.longitude === undefined ? null : d.longitude,
      workingHours: (d.workingHours || []).map((h) => ({ day: h.day, opens: h.opens, closes: h.closes })),
      repairMethods: d.repairMethods || [],
      availability: d.availability || 'AVAILABLE',
      availabilityNote: d.availabilityNote || null,
    };
  }

  // ------------------------------------------------------------------ section renderers
  function businessHtml(d) {
    return `<div class="row g-3">
      <div class="col-md-7"><label class="form-label required" for="rp-businessName">Business name</label>
        <input class="form-control" id="rp-businessName" name="businessName" required minlength="3" maxlength="150" value="${esc(d.businessName || '')}">
        <div class="invalid-feedback">3–150 characters.</div></div>
      <div class="col-md-5"><label class="form-label" for="rp-phone">Public phone</label>
        <input class="form-control" id="rp-phone" name="phone" type="tel" pattern="^\\+?[0-9]{10,15}$" value="${esc(d.phone || '')}">
        <div class="invalid-feedback">10–15 digits, optionally starting with +.</div></div>
      <div class="col-12"><label class="form-label required" for="rp-description">About your workshop</label>
        <textarea class="form-control" id="rp-description" name="description" rows="4" required minlength="20" maxlength="2000" placeholder="What you repair, your experience, parts you use, warranty…">${esc(d.description || '')}</textarea>
        <div class="invalid-feedback">20–2000 characters.</div></div>
      <div class="col-md-5"><label class="form-label required" for="rp-availability">Availability</label>
        <select class="form-select" id="rp-availability" name="availability" required>${options(ENUMS.availability, d.availability || 'AVAILABLE')}</select></div>
      <div class="col-md-7"><label class="form-label" for="rp-availabilityNote">Availability note</label>
        <input class="form-control" id="rp-availabilityNote" name="availabilityNote" maxlength="200" value="${esc(d.availabilityNote || '')}" placeholder="e.g. Closed for Diwali until 3 Nov"></div>
    </div>`;
  }

  function serviceRow(s, i) {
    return `<div class="border rounded p-3 position-relative" data-service-row>
      <input type="hidden" name="svc-id" value="${esc(s.id || '')}">
      <div class="row g-2">
        <div class="col-md-6"><label class="form-label small required" for="svc-name-${i}">Service name</label>
          <input class="form-control" id="svc-name-${i}" name="svc-name" required minlength="3" maxlength="150" value="${esc(s.name || '')}" data-field="services[${i}].name"></div>
        <div class="col-md-6"><label class="form-label small required" for="svc-cat-${i}">Category</label>
          <select class="form-select" id="svc-cat-${i}" name="svc-category" required data-field="services[${i}].category">${options(ENUMS.repairCategory, s.category, 'Choose')}</select></div>
        <div class="col-12"><label class="form-label small" for="svc-desc-${i}">Description</label>
          <input class="form-control" id="svc-desc-${i}" name="svc-description" maxlength="500" value="${esc(s.description || '')}"></div>
        <div class="col-md-4"><label class="form-label small required" for="svc-pt-${i}">Price type</label>
          <select class="form-select" id="svc-pt-${i}" name="svc-priceType" required>${options(ENUMS.priceType, s.priceType || 'FIXED')}</select></div>
        <div class="col-md-4"><label class="form-label small required" for="svc-price-${i}">Price (₹)</label>
          <input class="form-control" id="svc-price-${i}" name="svc-price" type="number" min="1" max="1000000" step="0.01" required value="${esc(s.price || '')}" data-field="services[${i}].price"></div>
        <div class="col-md-4"><label class="form-label small" for="svc-days-${i}">Estimated days</label>
          <input class="form-control" id="svc-days-${i}" name="svc-days" type="number" min="1" max="60" value="${esc(s.estimatedDays || '')}"></div>
      </div>
      <button type="button" class="btn btn-sm btn-link text-danger position-absolute top-0 end-0" data-remove-service aria-label="Remove service"><i class="bi bi-x-lg"></i></button>
    </div>`;
  }

  function servicesHtml(d) {
    return `<div class="mb-3"><span class="form-label required d-block">Repair categories</span>
        <div class="check-grid" data-field="categories">${ENUMS.repairCategory.map((c) => `<div class="form-check"><input class="form-check-input" type="checkbox" name="categories" value="${c}" id="rp-cat-${c}" ${(d.categories || []).includes(c) ? 'checked' : ''}><label class="form-check-label" for="rp-cat-${c}">${esc(label(c))}</label></div>`).join('')}</div></div>
      <div class="mb-3"><label class="form-label" for="rp-expertise">Expertise tags</label>
        <input class="form-control" id="rp-expertise" name="expertise" value="${esc((d.expertise || []).join(', '))}" placeholder="Screen replacement, Battery swap">
        <div class="form-text">Comma-separated, up to 10, each 2–40 characters.</div></div>
      <div class="d-flex justify-content-between align-items-center mb-2"><span class="form-label mb-0 required">Services (1–30)</span>
        <button type="button" class="btn btn-sm btn-outline-primary" data-add-service><i class="bi bi-plus-lg"></i> Add service</button></div>
      <div class="d-grid gap-2" data-services data-field="services">${(d.services && d.services.length ? d.services : [{}]).map(serviceRow).join('')}</div>
      <div class="form-text mt-2">Prices are what you charge before ReLoop's fees. When you quote, the customer's platform fee is added on top and your commission is deducted; both are shown to you and the customer.</div>`;
  }

  function locationHtml(d) {
    return `<div class="row g-3">
      <div class="col-12"><label class="form-label required" for="rp-addressLine">Workshop address</label>
        <input class="form-control" id="rp-addressLine" name="addressLine" required maxlength="255" value="${esc(d.addressLine || '')}"></div>
      <div class="col-md-7"><label class="form-label required" for="rp-city">City</label>
        <input class="form-control" id="rp-city" name="city" required maxlength="120" value="${esc(d.city || '')}"></div>
      <div class="col-md-5"><label class="form-label required" for="rp-pincode">PIN code</label>
        <input class="form-control" id="rp-pincode" name="pincode" required pattern="^[1-9][0-9]{5}$" maxlength="6" inputmode="numeric" value="${esc(d.pincode || '')}">
        <div class="invalid-feedback">6-digit PIN code.</div></div>
      <div class="col-12"><div class="map-box" data-pick-map role="application" aria-label="Map: click to set your workshop location"></div></div>
      <div class="col-md-4"><label class="form-label required" for="rp-latitude">Latitude</label>
        <input class="form-control" id="rp-latitude" name="latitude" type="number" step="any" min="-90" max="90" required value="${esc(d.latitude !== null && d.latitude !== undefined ? d.latitude : '')}"></div>
      <div class="col-md-4"><label class="form-label required" for="rp-longitude">Longitude</label>
        <input class="form-control" id="rp-longitude" name="longitude" type="number" step="any" min="-180" max="180" required value="${esc(d.longitude !== null && d.longitude !== undefined ? d.longitude : '')}"></div>
      <div class="col-md-4 d-flex align-items-end"><button class="btn btn-light border w-100" type="button" data-use-location><i class="bi bi-crosshair me-1"></i>Use my location</button></div>
      <div class="col-12"><span class="form-label required d-block">How customers can hand over items</span>
        <div class="check-grid" data-field="repairMethods">${ENUMS.repairMethod.map((m) => `<div class="form-check"><input class="form-check-input" type="checkbox" name="repairMethods" value="${m}" id="rp-rm-${m}" ${(d.repairMethods || []).includes(m) ? 'checked' : ''}><label class="form-check-label" for="rp-rm-${m}">${esc(label(m))}</label></div>`).join('')}</div></div>
    </div>`;
  }

  function hoursHtml(d) {
    const byDay = {};
    (d.workingHours || []).forEach((h) => { byDay[h.day] = h; });
    const fresh = !(d.workingHours || []).length;
    return `<div class="table-responsive"><table class="table rl-table align-middle mb-0" data-field="workingHours"><thead><tr><th>Day</th><th>Open</th><th>Opens</th><th>Closes</th></tr></thead><tbody>
      ${ENUMS.days.map((day) => {
        const h = byDay[day];
        const open = h || (fresh && day !== 'SUNDAY');
        return `<tr data-day="${day}"><td class="fw-semibold">${esc(label(day))}</td>
          <td><div class="form-check form-switch m-0"><input class="form-check-input" type="checkbox" name="open-${day}" id="rp-open-${day}" ${open ? 'checked' : ''} aria-label="Open on ${esc(label(day))}"></div></td>
          <td><input class="form-control form-control-sm" type="time" name="opens-${day}" value="${esc(h ? h.opens : '10:00')}" aria-label="${esc(label(day))} opening time"></td>
          <td><input class="form-control form-control-sm" type="time" name="closes-${day}" value="${esc(h ? h.closes : '19:00')}" aria-label="${esc(label(day))} closing time"></td></tr>`;
      }).join('')}</tbody></table></div>
      ${fresh ? '<div class="form-text">Suggested hours are pre-filled. They are only saved when you press Save.</div>' : ''}`;
  }

  const RENDER = { business: businessHtml, services: servicesHtml, location: locationHtml, hours: hoursHtml };

  // ------------------------------------------------------------------ readers (form -> partial request)
  function readBusiness(form, v) {
    return { businessName: v.businessName, description: v.description, phone: v.phone || null, availability: v.availability, availabilityNote: v.availabilityNote || null };
  }
  function readServices(form, v) {
    const services = $$('[data-service-row]', form).map((row) => {
      const g = (n) => row.querySelector(`[name="${n}"]`).value.trim();
      return { id: g('svc-id') ? Number(g('svc-id')) : null, name: g('svc-name'), description: g('svc-description') || null, category: g('svc-category'), priceType: g('svc-priceType'), price: Number(g('svc-price')), estimatedDays: g('svc-days') ? Number(g('svc-days')) : null };
    });
    return { categories: ui.checkedValues(form, 'categories'), expertise: String(v.expertise || '').split(',').map((s) => s.trim()).filter(Boolean), services };
  }
  function readLocation(form, v) {
    return { addressLine: v.addressLine, city: v.city, pincode: v.pincode, latitude: Number(v.latitude), longitude: Number(v.longitude), repairMethods: ui.checkedValues(form, 'repairMethods') };
  }
  function readHours(form) {
    return {
      workingHours: ENUMS.days.filter((day) => form.querySelector(`[name="open-${day}"]`).checked)
        .map((day) => ({ day, opens: form.querySelector(`[name="opens-${day}"]`).value, closes: form.querySelector(`[name="closes-${day}"]`).value })),
    };
  }
  const READ = { business: readBusiness, services: readServices, location: readLocation, hours: readHours };

  function clientChecks(form, body, sections) {
    const fe = {};
    if (sections.includes('services')) {
      if (!body.categories.length) fe.categories = 'choose at least one category';
      if (body.categories.length > 8) fe.categories = 'choose at most 8 categories';
      if (body.expertise.length > 10 || body.expertise.some((e) => e.length < 2 || e.length > 40)) fe.expertise = 'up to 10 tags, each 2–40 characters';
      if (!body.services.length) fe.services = 'add at least one service';
    }
    if (sections.includes('location') && !body.repairMethods.length) fe.repairMethods = 'choose at least one method';
    if (sections.includes('hours')) {
      if (!body.workingHours.length) fe.workingHours = 'open on at least one day';
      else if (body.workingHours.some((h) => !h.opens || !h.closes || h.opens >= h.closes)) fe.workingHours = 'each open day needs an opening time before its closing time';
    }
    return fe;
  }

  /** Render the editor for the given sections (+ any other section still missing). */
  async function mountEditor(container, sections, opts) {
    const o = opts || {};
    ui.loader(container, 'Loading your workshop profile…');
    let detail;
    try {
      detail = await me(true);
    } catch (err) {
      ui.error(container, err, () => mountEditor(container, sections, opts), 'Could not load your workshop profile');
      return;
    }
    if (!detail) {
      container.innerHTML = `<div class="card">${ui.emptyHtml({ icon: 'bi-shop', title: 'No workshop profile found', text: 'Your account has no repairer profile in repair-service. Registration normally creates one; please contact ReLoop support.' })}</div>`;
      return;
    }
    const missingSections = [...new Set((detail.missingForListing || []).map((m) => SECTION_OF[m]).filter(Boolean))];
    const extra = missingSections.filter((s) => !sections.includes(s));
    const all = sections.concat(extra);

    container.innerHTML = `<form id="rp-form" novalidate class="d-grid gap-4">
      ${detail.missingForListing && detail.missingForListing.length ? `<div class="alert alert-warning mb-0"><div class="fw-semibold"><i class="bi bi-eye-slash me-1"></i>Your workshop is hidden from customer search</div>
        <div class="small">Still missing: ${esc(missingText(detail.missingForListing))}.${extra.length ? ' ReLoop saves the whole profile at once, so the missing sections are included below. Complete them to save.' : ''}</div></div>`
        : `<div class="alert alert-success mb-0 small"><i class="bi bi-eye me-1"></i>Your workshop is listed in customer search. <a href="/repairer.html?id=${encodeURIComponent(detail.id)}">View public page</a></div>`}
      ${all.map((s) => `<section class="card" data-section="${s}"><div class="card-header d-flex justify-content-between align-items-center"><span>${esc(SECTION_TITLE[s])}</span>${extra.includes(s) ? '<span class="chip">Required to save</span>' : ''}</div>
        <div class="card-body">${RENDER[s](detail)}</div></section>`).join('')}
      <div class="d-flex gap-2"><button class="btn btn-primary btn-lg" type="submit">Save changes</button><button class="btn btn-light border btn-lg" type="button" data-reset>Discard changes</button></div>
    </form>`;

    const form = $('#rp-form', container);
    $('[data-reset]', form).addEventListener('click', () => mountEditor(container, sections, opts));

    if (all.includes('services')) {
      const list = $('[data-services]', form);
      const renumber = () => $$('[data-service-row]', list).forEach((row, i) => {
        $$('[data-field]', row).forEach((el) => { el.dataset.field = el.dataset.field.replace(/services\[\d+\]/, `services[${i}]`); });
      });
      $('[data-add-service]', form).addEventListener('click', () => {
        if ($$('[data-service-row]', list).length >= 30) { ui.toast('You can list up to 30 services.', 'warning'); return; }
        list.insertAdjacentHTML('beforeend', serviceRow({}, Date.now()));
        renumber();
        list.lastElementChild.querySelector('input[name="svc-name"]').focus();
      });
      list.addEventListener('click', (ev) => {
        const b = ev.target.closest('[data-remove-service]');
        if (!b) return;
        if ($$('[data-service-row]', list).length <= 1) { ui.toast('Keep at least one service.', 'warning'); return; }
        b.closest('[data-service-row]').remove();
        renumber();
      });
    }
    if (all.includes('location') && RL.locationPicker) {
      RL.locationPicker.attach({ map: $('[data-pick-map]', form), lat: $('#rp-latitude', form), lng: $('#rp-longitude', form), button: $('[data-use-location]', form) });
    }

    form.addEventListener('submit', async (ev) => {
      ev.preventDefault();
      ui.clearErrors(form);
      if (!ui.validate(form)) return;
      const v = ui.formValues(form);
      const body = toRequest(detail);
      all.forEach((s) => Object.assign(body, READ[s](form, v)));
      const fe = clientChecks(form, body, all);
      if (Object.keys(fe).length) { ui.showErrors(form, { message: 'Please fix the highlighted sections.', fieldErrors: fe }); return; }
      try {
        const saved = await ui.busy(form.querySelector('[type=submit]'), () => RL.endpoints.repairers.update(detail.id, body), 'Saving…');
        mePromise = Promise.resolve(saved);
        ui.toast(saved.listedInSearch ? 'Profile saved. Your workshop is listed in search.' : 'Profile saved.', 'success');
        if (o.onSaved) o.onSaved(saved);
        mountEditor(container, sections, opts);
      } catch (err) {
        ui.showErrors(form, err);
      }
    });
  }

  RL.repairerProfile = { me, toRequest, mountEditor, missingText, SECTION_OF, SECTION_TITLE, SECTION_PAGE };
})(window);
