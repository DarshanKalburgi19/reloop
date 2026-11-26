/*
 * Simulated delivery tracking component (repair-service /api/tracking).
 *
 * Everything here is a SIMULATION: positions move along a predefined route between demo locations in Gujarat only
 * when a party presses "Demo: advance". It is never described as live, GPS or a courier integration.
 *
 *   RL.tracking.renderDetail(container, delivery, {onChange})   full card: notice, stepper, map, facts, history
 *   RL.tracking.legsHtml(summaries)                             compact list of legs (DeliverySummaryResponse[])
 *   RL.tracking.bookDialog({deliveryType, orderId?, repairRequestId?, needOrigin, needDestination})
 *   RL.map.load() / RL.map.create(el, opts)                     lazy Leaflet loader shared with other pages
 */
(function (window, document) {
  'use strict';

  const RL = window.RL = window.RL || {};
  const { esc, label, badge, dateTime, relative } = RL.ui;

  // ---------------------------------------------------------------- Leaflet (lazy, pinned with SRI)
  const LEAFLET = {
    css: 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.css',
    cssSri: 'sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=',
    js: 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js',
    jsSri: 'sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=',
  };
  let leafletPromise = null;
  function loadLeaflet() {
    if (window.L) return Promise.resolve(window.L);
    if (leafletPromise) return leafletPromise;
    leafletPromise = new Promise((resolve, reject) => {
      const link = document.createElement('link');
      link.rel = 'stylesheet';
      link.href = LEAFLET.css;
      link.integrity = LEAFLET.cssSri;
      link.crossOrigin = '';
      document.head.appendChild(link);
      const script = document.createElement('script');
      script.src = LEAFLET.js;
      script.integrity = LEAFLET.jsSri;
      script.crossOrigin = '';
      script.onload = () => (window.L ? resolve(window.L) : reject(new Error('Map library failed to initialise')));
      script.onerror = () => { leafletPromise = null; reject(new Error('The map library could not be loaded (offline?)')); };
      document.head.appendChild(script);
    });
    return leafletPromise;
  }
  /** Create a map with OpenStreetMap tiles. Resolves to the Leaflet map, or renders a notice if Leaflet fails. */
  async function createMap(el, opts) {
    try {
      const L = await loadLeaflet();
      const o = opts || {};
      const map = L.map(el, { scrollWheelZoom: false }).setView(o.center || [22.6, 72.2], o.zoom || 7);
      L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 18,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      }).addTo(map);
      return map;
    } catch (err) {
      el.innerHTML = `<div class="h-100 d-grid place-items-center text-center p-4 text-muted small"><div><i class="bi bi-map fs-3 d-block mb-1"></i>${esc(err.message)}</div></div>`;
      return null;
    }
  }
  function dot(L, color) {
    return L.divIcon({
      className: '',
      html: `<span style="display:block;width:16px;height:16px;border-radius:50%;background:${color};border:3px solid #fff;box-shadow:0 0 0 1px rgba(0,0,0,.25)"></span>`,
      iconSize: [16, 16],
      iconAnchor: [8, 8],
    });
  }
  RL.map = { load: loadLeaflet, create: createMap, dot };

  // ---------------------------------------------------------------- detail
  const NOTICE = 'Simulated delivery: the position moves along a predefined route between demo locations in Gujarat only when someone presses "Demo: advance". No GPS, phone location or courier service is used.';

  function stepper(d) {
    const flow = d.statusFlow && d.statusFlow.length ? d.statusFlow : ['BOOKED', 'PICKED_UP', 'IN_TRANSIT', 'NEAR_DESTINATION', 'DELIVERED'];
    const idx = flow.indexOf(d.status);
    return `<div class="stepper" aria-label="Delivery progress">${flow.map((s, i) => {
      const cls = i < idx ? 'done' : (i === idx ? (d.status === 'DELIVERED' ? 'done current' : 'current') : '');
      return `<div class="step ${cls}"${i === idx ? ' aria-current="step"' : ''}><div class="bar"></div>${esc(label(s))}</div>`;
    }).join('')}</div>`;
  }

  function historyHtml(points) {
    if (!points || !points.length) return '<p class="text-muted small mb-0">No checkpoints recorded yet.</p>';
    return `<ul class="timeline">${points.slice().reverse().map((p) => `<li>
      <div class="tl-title">${esc(label(p.status))} · ${esc(p.progressPercent)}%</div>
      <div class="small">${esc(p.note || '')}</div>
      <div class="tl-meta">${esc(dateTime(p.recordedAt))} · ${esc(Number(p.remainingKm).toFixed(1))} km remaining</div></li>`).join('')}</ul>`;
  }

  /**
   * Render a DeliveryResponse with map + advance button. opts.onChange(updated) is called after a successful advance.
   * Returns a handle {refresh()}.
   */
  function renderDetail(container, delivery, opts) {
    const o = opts || {};
    let d = delivery;
    let map = null;
    let layers = [];

    function draw() {
      container.innerHTML = `<div class="card"><div class="card-body d-grid gap-3">
        <div class="simulated-banner" role="note"><i class="bi bi-info-circle" aria-hidden="true"></i><span>${esc(d.simulationNotice || NOTICE)}</span></div>
        <div class="d-flex flex-wrap justify-content-between align-items-start gap-2">
          <div><div class="small-caps">Tracking number</div><div class="h5 mb-1 mono">${esc(d.trackingNumber)}</div>
            <div class="small text-muted">${esc(d.deliveryTypeLabel || label(d.deliveryType))}${d.referenceNumber ? ' · ' + esc(d.referenceNumber) : ''}${d.carrier ? ' · ' + esc(d.carrier) : ''}</div></div>
          <div class="text-end">${badge(d.status, d.statusLabel)}<div class="small text-muted mt-1">Updated ${esc(relative(d.lastUpdatedAt))}</div></div>
        </div>
        ${stepper(d)}
        <div>
          <div class="d-flex justify-content-between small mb-1"><span>${esc(d.progressPercent)}% of route</span><span>${esc(Number(d.remainingDistanceKm || 0).toFixed(1))} of ${esc(Number(d.routeDistanceKm || 0).toFixed(1))} km remaining</span></div>
          <div class="progress" role="progressbar" aria-label="Simulated route progress" aria-valuenow="${esc(d.progressPercent)}" aria-valuemin="0" aria-valuemax="100" style="height:8px"><div class="progress-bar" style="width:${Number(d.progressPercent) || 0}%"></div></div>
        </div>
        <div class="row g-3">
          <div class="col-lg-7"><div class="map-box" data-map role="img" aria-label="Map of the simulated route"></div>
            <div class="small text-muted mt-1"><span class="me-3"><span class="d-inline-block rounded-circle me-1" style="width:10px;height:10px;background:#1f5c45"></span>Origin</span><span class="me-3"><span class="d-inline-block rounded-circle me-1" style="width:10px;height:10px;background:#a1302a"></span>Destination</span><span><span class="d-inline-block rounded-circle me-1" style="width:10px;height:10px;background:#c58a1e"></span>Simulated position</span></div></div>
          <div class="col-lg-5">
            <dl class="dl-grid mb-3">
              <dt>From</dt><dd>${esc(d.origin ? d.origin.label : '—')}${d.origin && d.origin.city ? `, ${esc(d.origin.city)}` : ''}</dd>
              <dt>To</dt><dd>${esc(d.destination ? d.destination.label : '—')}${d.destination && d.destination.city ? `, ${esc(d.destination.city)}` : ''}</dd>
              <dt>Booked</dt><dd>${esc(dateTime(d.bookedAt))}</dd>
              <dt>Picked up</dt><dd>${esc(dateTime(d.pickedUpAt))}</dd>
              <dt>Estimated arrival</dt><dd>${d.status === 'DELIVERED' ? 'Delivered' : esc(dateTime(d.estimatedArrivalAt))} <span class="text-muted small">(simulated)</span></dd>
              <dt>Delivered</dt><dd>${esc(dateTime(d.deliveredAt))}</dd>
            </dl>
            <div class="d-flex flex-wrap gap-2">
              <button type="button" class="btn btn-primary btn-sm" data-advance ${d.canAdvance ? '' : 'disabled'}>
                <i class="bi bi-skip-forward" aria-hidden="true"></i> Demo: advance${d.nextStatus ? ' to ' + esc(label(d.nextStatus)) : ''}</button>
              <button type="button" class="btn btn-light btn-sm" data-refresh><i class="bi bi-arrow-clockwise" aria-hidden="true"></i> Refresh</button>
            </div>
            ${d.canAdvance ? '' : `<div class="small text-muted mt-2">${d.status === 'DELIVERED' ? 'This simulated delivery is complete.' : 'You cannot advance this delivery.'}</div>`}
          </div>
        </div>
        <div><h3 class="h6">Checkpoint history</h3>${historyHtml(d.history)}</div>
      </div></div>`;

      container.querySelector('[data-advance]').addEventListener('click', (ev) => advance(ev.currentTarget));
      container.querySelector('[data-refresh]').addEventListener('click', (ev) => refresh(ev.currentTarget));
      map = null;
      drawMap();
    }

    async function drawMap() {
      const el = container.querySelector('[data-map]');
      if (!el) return;
      map = await createMap(el, { center: d.currentPosition ? [d.currentPosition.latitude, d.currentPosition.longitude] : undefined });
      if (!map) return;
      const L = window.L;
      layers.forEach((l) => l.remove());
      layers = [];
      const route = (d.route || []).filter((p) => Array.isArray(p) && p.length >= 2);
      if (route.length) layers.push(L.polyline(route, { color: '#1f5c45', weight: 4, opacity: 0.55, dashArray: '6 8' }).addTo(map));
      const travelled = (d.history || []).map((p) => [p.latitude, p.longitude]);
      if (travelled.length > 1) layers.push(L.polyline(travelled, { color: '#2b5a8a', weight: 4 }).addTo(map));
      if (d.origin) layers.push(L.marker([d.origin.latitude, d.origin.longitude], { icon: dot(L, '#1f5c45'), title: 'Origin' }).bindTooltip('Origin: ' + d.origin.label).addTo(map));
      if (d.destination) layers.push(L.marker([d.destination.latitude, d.destination.longitude], { icon: dot(L, '#a1302a'), title: 'Destination' }).bindTooltip('Destination: ' + d.destination.label).addTo(map));
      if (d.currentPosition) layers.push(L.marker([d.currentPosition.latitude, d.currentPosition.longitude], { icon: dot(L, '#c58a1e'), title: 'Simulated position' }).bindTooltip('Simulated position').addTo(map));
      const pts = route.concat(d.origin ? [[d.origin.latitude, d.origin.longitude]] : [], d.destination ? [[d.destination.latitude, d.destination.longitude]] : []);
      if (pts.length) map.fitBounds(pts, { padding: [24, 24] });
    }

    async function advance(btn) {
      try {
        d = await RL.ui.busy(btn, () => RL.endpoints.tracking.advance(d.trackingNumber), 'Advancing…');
        RL.ui.toast(`Simulated delivery moved to "${label(d.status)}".`, 'success');
        draw();
        if (o.onChange) o.onChange(d);
      } catch (err) {
        RL.ui.toastError(err, 'Could not advance the delivery');
      }
    }

    async function refresh(btn) {
      try {
        d = await RL.ui.busy(btn, () => RL.endpoints.tracking.get(d.trackingNumber), 'Refreshing…');
        draw();
        if (o.onChange) o.onChange(d);
      } catch (err) {
        RL.ui.toastError(err, 'Could not refresh the delivery');
      }
    }

    draw();
    return { refresh: () => refresh(null) };
  }

  /** DeliverySummaryResponse[] -> list group; each item has data-tn for the caller to bind. */
  function legsHtml(list, activeTn) {
    if (!list || !list.length) return '';
    return `<div class="list-group">${list.map((s) => `<button type="button" class="list-group-item list-group-item-action d-flex justify-content-between align-items-center gap-2${s.trackingNumber === activeTn ? ' active' : ''}" data-tn="${esc(s.trackingNumber)}">
      <span class="min-w-0"><span class="d-block fw-semibold mono small">${esc(s.trackingNumber)}</span>
      <span class="d-block small ${s.trackingNumber === activeTn ? '' : 'text-muted'} text-truncate">${esc(label(s.deliveryType))} · ${esc(s.originCity || s.originLabel || '')} → ${esc(s.destinationCity || s.destinationLabel || '')}</span></span>
      <span class="text-end">${badge(s.status)}<span class="d-block small mt-1">${esc(s.progressPercent)}%</span></span></button>`).join('')}</div>`;
  }

  let locationsPromise = null;
  function demoLocations() {
    if (!locationsPromise) {
      locationsPromise = RL.endpoints.tracking.demoLocations().catch((err) => { locationsPromise = null; throw err; });
    }
    return locationsPromise;
  }

  /**
   * Booking dialog. o: {deliveryType, orderId?, repairRequestId?, needOrigin (bool), needDestination (bool), title}.
   * Resolves to the created DeliveryResponse, or null if dismissed.
   */
  async function bookDialog(o) {
    let locations;
    try {
      locations = await demoLocations();
    } catch (err) {
      RL.ui.toastError(err, 'Could not load the demo locations');
      return null;
    }
    const opts = locations.map((l) => ({ value: l.code, label: `${l.label} (${l.city})` }));
    const field = (name, text, required) => `<div class="mb-3"><label class="form-label ${required ? 'required' : ''}" for="bk-${name}">${esc(text)}</label>
      <select class="form-select" id="bk-${name}" name="${name}" ${required ? 'required' : ''}>${RL.ui.options(opts, '', required ? 'Choose a demo location' : 'Use the default')}</select>
      <div class="invalid-feedback">Choose a demo location.</div></div>`;
    return RL.ui.modal({
      title: o.title || 'Book a simulated delivery',
      confirmLabel: 'Book delivery',
      busyLabel: 'Booking…',
      body: `<div class="simulated-banner mb-3"><i class="bi bi-info-circle" aria-hidden="true"></i><span>${esc(NOTICE)}</span></div>
        <p class="small text-muted">${esc(label(o.deliveryType))}. Pick from ReLoop's predefined demo locations in Gujarat.</p>
        ${o.needOrigin !== false ? field('originCode', 'Pickup from', !!o.needOrigin) : ''}
        ${o.needDestination !== false ? field('destinationCode', 'Deliver to', !!o.needDestination) : ''}`,
      onSubmit: async (form) => {
        const v = RL.ui.formValues(form);
        const created = await RL.endpoints.tracking.book({
          deliveryType: o.deliveryType,
          orderId: o.orderId || null,
          repairRequestId: o.repairRequestId || null,
          originCode: v.originCode || null,
          destinationCode: v.destinationCode || null,
        });
        RL.ui.toast(`Simulated delivery ${created.trackingNumber} booked.`, 'success');
        return created;
      },
    });
  }

  RL.tracking = { renderDetail, legsHtml, bookDialog, demoLocations, NOTICE };
})(window, document);
