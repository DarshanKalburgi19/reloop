/*
 * Workshop details: GET /api/repairers/{id} (services, hours, methods, reviews, map) and the repair-request form
 * (POST /api/repair-requests, customers only). Photos are chosen from the bundled demo images
 * (GET /api/repair-requests/demo-images) or given as https links; nothing is uploaded.
 */
RL.page(async ({ account }) => {
  const { $, $$, esc, label, options, rupees, date, percent } = RL.ui;
  const ui = RL.ui;
  const id = ui.param('id');
  const host = $('#repairer');
  if (!id || !/^\d+$/.test(id)) {
    host.innerHTML = `<div class="card">${ui.emptyHtml({ icon: 'bi-shop', title: 'No workshop selected', text: 'Pick a workshop from the list.', actionHref: '/repairers.html', actionLabel: 'Find a repairer' })}</div>`;
    return;
  }
  const DAY_SHORT = { MONDAY: 'Mon', TUESDAY: 'Tue', WEDNESDAY: 'Wed', THURSDAY: 'Thu', FRIDAY: 'Fri', SATURDAY: 'Sat', SUNDAY: 'Sun' };
  let d;

  function hoursHtml() {
    const by = {};
    (d.workingHours || []).forEach((h) => { by[h.day] = h; });
    return `<table class="table table-sm mb-0"><tbody>${ui.ENUMS.days.map((day) => `<tr><th scope="row" class="fw-normal">${esc(label(day))}</th><td class="text-end">${by[day] ? `${esc(by[day].opens)}–${esc(by[day].closes)}` : '<span class="text-muted">Closed</span>'}</td></tr>`).join('')}</tbody></table>`;
  }

  function servicesHtml() {
    if (!(d.services || []).length) return ui.emptyHtml({ icon: 'bi-list-check', title: 'No services listed yet' });
    return `<div class="table-responsive"><table class="table rl-table mb-0"><thead><tr><th>Service</th><th>Category</th><th class="num">Price</th><th class="num">Time</th></tr></thead><tbody>
      ${d.services.map((s) => `<tr><td class="fw-semibold">${esc(s.name)}${s.description ? `<div class="small text-muted fw-normal">${esc(s.description)}</div>` : ''}</td><td><span class="chip">${esc(label(s.category))}</span></td>
        <td class="num">${esc(s.priceLabel || rupees(s.price))}</td><td class="num small">${s.estimatedDays ? `${s.estimatedDays} day${s.estimatedDays > 1 ? 's' : ''}` : '—'}</td></tr>`).join('')}
    </tbody></table></div>`;
  }

  function reviewsHtml() {
    const list = d.recentReviews || [];
    if (!list.length) return ui.emptyHtml({ icon: 'bi-chat-square-text', title: 'No reviews yet' });
    return `<ul class="list-group list-group-flush">${list.map((r) => `<li class="list-group-item">
      <div class="d-flex justify-content-between"><span class="fw-semibold">${esc(r.reviewerName || 'Customer')}</span><span>${RL.cards.stars(r.rating)}</span></div>
      ${r.comment ? `<p class="mb-1 small">${esc(r.comment)}</p>` : ''}<div class="small text-muted">${esc(date(r.createdAt))}${r.demo ? ' · demo review' : ''}</div></li>`).join('')}</ul>`;
  }

  function requestBoxHtml() {
    if (d.ownedByViewer) return `<div class="alert alert-info small mb-0">This is your workshop's public page. <a href="/repairer/">Go to your dashboard</a></div>`;
    if (!account) return `<p class="small text-muted">Describe the problem and the workshop will inspect it and send a quote. You approve the quote, with every fee shown, before any work starts.</p>
      <a class="btn btn-primary w-100" href="${esc(RL.layout.loginUrl(window.location.pathname + window.location.search))}">Sign in to request a repair</a>
      <div class="small text-center mt-2">New here? <a href="/register-customer.html">Create a customer account</a></div>`;
    if (account.role !== 'CUSTOMER') return '<div class="alert alert-secondary small mb-0">Repair requests are made from customer accounts.</div>';
    if (!d.listedInSearch) return '<div class="alert alert-secondary small mb-0">This workshop is not accepting requests yet.</div>';
    if (d.availability === 'UNAVAILABLE') return `<div class="alert alert-secondary small mb-0">This workshop is currently unavailable${d.availabilityNote ? ': ' + esc(d.availabilityNote) : ''}.</div>`;
    return `<p class="small text-muted">The workshop inspects your item and sends a quote. Nothing is charged until you approve and pay the quote.</p>
      <button type="button" class="btn btn-primary w-100" id="open-request"><i class="bi bi-tools me-1" aria-hidden="true"></i>Request a repair</button>`;
  }

  async function load() {
    ui.loader(host, 'Loading workshop…');
    try {
      d = await RL.endpoints.repairers.get(id);
    } catch (err) {
      if (err.status === 404) {
        host.innerHTML = `<div class="card">${ui.emptyHtml({ icon: 'bi-shop', title: 'Workshop not found', text: 'It may not be listed yet.', actionHref: '/repairers.html', actionLabel: 'Find a repairer' })}</div>`;
        return;
      }
      ui.error(host, err, load, 'Could not load this workshop');
      return;
    }
    document.title = `${d.businessName} — ReLoop`;
    $('#crumb').textContent = d.businessName;
    const methods = (d.repairMethods || []).map((m) => `<span class="chip"><i class="bi ${m === 'IN_PERSON' ? 'bi-shop' : m === 'SEND_ITEM' ? 'bi-box-seam' : 'bi-truck'}" aria-hidden="true"></i>${esc(label(m))}</span>`).join('');
    host.innerHTML = `<div class="row g-4 g-lg-5">
      <div class="col-lg-8 d-grid gap-4 align-content-start">
        <div class="card"><div class="card-body">
          <div class="d-flex flex-wrap gap-3 align-items-start">
            <span class="repairer-initial fs-4" aria-hidden="true">${esc(ui.initials(d.businessName))}</span>
            <div class="flex-grow-1 min-w-0">
              <h1 class="h3 mb-1">${esc(d.businessName)}</h1>
              <div class="d-flex flex-wrap gap-2 align-items-center small">${ui.badge(d.availability)}${d.verificationStatus === 'VERIFIED' ? '<span class="chip chip-green"><i class="bi bi-patch-check" aria-hidden="true"></i>Verified by ReLoop</span>' : ''}
                <span>${RL.cards.stars(d.rating && d.rating.count ? d.rating.average : null)}${d.rating && d.rating.count ? ` <span class="text-muted">(${d.rating.count} reviews)</span>` : ''}</span>
                <span class="text-muted"><i class="bi bi-geo-alt" aria-hidden="true"></i> ${esc(d.city || '')}</span></div>
              ${d.availabilityNote ? `<div class="small text-muted mt-1">${esc(d.availabilityNote)}</div>` : ''}
            </div>
            ${d.startingPriceLabel ? `<div class="text-end"><div class="small text-muted">Prices</div><div class="fw-semibold">${esc(d.startingPriceLabel)}</div></div>` : ''}
          </div>
          ${d.description ? `<p class="mt-3 mb-0 text-pre-wrap">${esc(d.description)}</p>` : ''}
          <div class="d-flex flex-wrap gap-2 mt-3">${(d.categories || []).map((c) => `<span class="chip">${esc(label(c))}</span>`).join('')}${(d.expertise || []).map((e) => `<span class="chip chip-muted">${esc(e)}</span>`).join('')}</div>
        </div></div>
        <section class="card"><div class="card-header">Services and prices</div>${servicesHtml()}
          <div class="card-body small text-muted border-top">Prices are the workshop's own. When you approve a quote, a ReLoop customer platform fee<span id="fee-rate"></span> is added and shown before you pay.</div></section>
        <section class="card"><div class="card-header">Reviews</div>${reviewsHtml()}</section>
      </div>
      <div class="col-lg-4 d-grid gap-4 align-content-start">
        <div class="card"><div class="card-body">${requestBoxHtml()}</div></div>
        <div class="card"><div class="card-header">Location</div>
          ${d.latitude !== null && d.longitude !== null ? '<div class="map-box rounded-0 border-0" id="map" role="application" aria-label="Workshop location map"></div>' : ''}
          <div class="card-body small"><div>${esc(d.addressLine || '')}</div><div>${esc(d.city || '')} ${esc(d.pincode || '')}</div>
            ${d.phone ? `<div class="mt-1"><i class="bi bi-telephone me-1" aria-hidden="true"></i><a href="tel:${esc(d.phone)}">${esc(d.phone)}</a></div>` : ''}
            <div class="mt-2"><span class="fw-semibold d-block mb-1">Hand-over</span><div class="d-flex flex-wrap gap-1">${methods || '<span class="text-muted">Not specified</span>'}</div></div></div></div>
        <div class="card"><div class="card-header">Opening hours</div><div class="card-body">${hoursHtml()}</div></div>
      </div>
    </div>`;

    if ($('#open-request')) $('#open-request').addEventListener('click', openRequest);
    RL.fees.rates().then((r) => { if (r.REPAIR_BOOKING && $('#fee-rate')) $('#fee-rate').textContent = ` (currently ${percent(r.REPAIR_BOOKING.customerFeeRate)})`; }).catch(() => {});
    if ($('#map')) {
      RL.map.create($('#map'), { center: [d.latitude, d.longitude], zoom: 14 }).then((m) => {
        if (m) window.L.marker([d.latitude, d.longitude], { icon: RL.map.dot(window.L, '#1f5c45'), title: d.businessName, alt: d.businessName }).addTo(m);
      });
    }
    if (ui.param('request') === '1' && $('#open-request')) openRequest();
  }

  async function openRequest() {
    let demo = [];
    try { demo = await RL.endpoints.repairRequests.demoImages(); } catch (e) { demo = []; }
    const svcOpts = (d.services || []).map((s) => `<option value="${esc(s.id)}" data-category="${esc(s.category)}">${esc(s.name)} · ${esc(s.priceLabel || rupees(s.price))}</option>`).join('');
    const created = await ui.modal({
      title: `Request a repair from ${d.businessName}`,
      size: 'lg',
      confirmLabel: 'Send request',
      busyLabel: 'Sending…',
      body: `<div class="row g-3">
        <div class="col-md-6"><label class="form-label" for="rq-service">Service</label>
          <select class="form-select" id="rq-service" name="repairServiceId"><option value="">Not sure / something else</option>${svcOpts}</select></div>
        <div class="col-md-6"><label class="form-label required" for="rq-category">Category</label>
          <select class="form-select" id="rq-category" name="category" required>${options(d.categories && d.categories.length ? d.categories : ui.ENUMS.repairCategory, (d.categories || [])[0], 'Choose')}</select><div class="invalid-feedback">Choose a category.</div></div>
        <div class="col-12"><label class="form-label required" for="rq-item">Item</label>
          <input class="form-control" id="rq-item" name="itemDescription" required minlength="3" maxlength="200" placeholder="e.g. Samsung Galaxy S21, black"><div class="invalid-feedback">3–200 characters.</div></div>
        <div class="col-12"><label class="form-label required" for="rq-problem">What's wrong?</label>
          <textarea class="form-control" id="rq-problem" name="problemDescription" rows="4" required minlength="20" maxlength="2000" placeholder="When did it start, what happens, anything you already tried…"></textarea><div class="invalid-feedback">Please describe the problem in at least 20 characters.</div></div>
        <div class="col-md-6"><label class="form-label required" for="rq-method">How will you hand it over?</label>
          <select class="form-select" id="rq-method" name="deliveryMethod" required>${options(d.repairMethods && d.repairMethods.length ? d.repairMethods : ui.ENUMS.repairMethod, (d.repairMethods || [])[0], 'Choose')}</select>
          <div class="form-text">Pickup and courier deliveries are simulated in this build.</div></div>
        <div class="col-md-6"><label class="form-label" for="rq-notes">Notes for the workshop</label>
          <input class="form-control" id="rq-notes" name="customerNotes" maxlength="1000" placeholder="Best time to call, accessories included…"></div>
        <div class="col-12" data-field="imageUrls"><span class="form-label d-block">Photos <span class="text-muted small fw-normal">(optional, up to 5)</span></span>
          ${demo.length ? `<div class="d-flex flex-wrap gap-2 mb-2">${demo.map((u, i) => `<input type="checkbox" class="btn-check" name="demoImage" id="rq-img-${i}" value="${esc(u)}"><label class="btn btn-outline-secondary p-1 image-pick" for="rq-img-${i}"><img src="${esc(u)}" alt="${esc(u.split('/').pop().replace('.svg', '').replace(/-/g, ' '))}" width="72" height="72"></label>`).join('')}</div>
            <div class="form-text mb-2">Photo upload isn't available yet. Pick a sample illustration and/or paste https links to your photos.</div>` : ''}
          <textarea class="form-control" id="rq-links" name="imageLinks" rows="2" placeholder="https://… (one link per line)" aria-label="Photo links"></textarea></div>
      </div>`,
      onRender: (el) => {
        el.querySelector('#rq-service').addEventListener('change', (ev) => {
          const opt = ev.target.selectedOptions[0];
          if (opt && opt.dataset.category) el.querySelector('#rq-category').value = opt.dataset.category;
        });
      },
      onSubmit: async (form) => {
        const f = ui.formValues(form);
        const links = String(f.imageLinks || '').split(/\s+/).map((s) => s.trim()).filter(Boolean);
        const bad = links.filter((u) => !/^https:\/\/\S+$/.test(u));
        const imageUrls = ui.checkedValues(form, 'demoImage').concat(links);
        if (bad.length || imageUrls.length > 5) {
          throw Object.assign(new Error('Please check the photos.'), { fieldErrors: { imageUrls: bad.length ? 'links must start with https://' : 'up to 5 photos' } });
        }
        return RL.endpoints.repairRequests.create({
          repairerProfileId: d.id,
          repairServiceId: f.repairServiceId ? Number(f.repairServiceId) : null,
          category: f.category,
          itemDescription: f.itemDescription,
          problemDescription: f.problemDescription,
          imageUrls,
          deliveryMethod: f.deliveryMethod,
          customerNotes: f.customerNotes || null,
        });
      },
    });
    if (created && created.id) {
      ui.flash(`Repair request ${created.requestNumber} sent to ${d.businessName}. You'll be notified here when they respond.`, 'success');
      window.location.href = '/customer/repair-request.html?id=' + encodeURIComponent(created.id);
    }
  }

  load();
});
