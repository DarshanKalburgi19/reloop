/*
 * Find a repairer: GET /api/repairers (filters + paging) or GET /api/repairers/nearby (lat/lng + radius, nearest first),
 * with results on an OpenStreetMap (Leaflet) map. Filters live in the URL so a search can be shared or refreshed.
 */
RL.page(async () => {
  const { $, $$, esc, options, ENUMS, label } = RL.ui;
  const ui = RL.ui;
  const CITIES = [
    { name: 'Ahmedabad', lat: 23.0225, lng: 72.5714 },
    { name: 'Surat', lat: 21.1702, lng: 72.8311 },
    { name: 'Vadodara', lat: 22.3072, lng: 73.1812 },
    { name: 'Rajkot', lat: 22.3039, lng: 70.8022 },
    { name: 'Gandhinagar', lat: 23.2156, lng: 72.6369 },
  ];
  const form = $('#rf');
  $('#rf-category').innerHTML = options(ENUMS.repairCategory, '', 'Any category');
  $('#rf-method').innerHTML = options(ENUMS.repairMethod, '', 'Any method');
  $('#near-cities').innerHTML = CITIES.map((c) => `<button type="button" class="btn btn-light border" data-city="${esc(c.name)}">${esc(c.name)}</button>`).join('');

  const url = new URLSearchParams(window.location.search);
  const state = {
    q: url.get('q') || '', category: url.get('category') || '', method: url.get('method') || '', city: url.get('city') || '',
    sort: url.get('sort') || 'rating', availableOnly: url.get('availableOnly') === 'true',
    lat: url.get('lat') ? Number(url.get('lat')) : null, lng: url.get('lng') ? Number(url.get('lng')) : null,
    radiusKm: url.get('radiusKm') || '10', near: url.get('near') || '', page: Number(url.get('page') || 0),
  };
  $('#rf-q').value = state.q; $('#rf-category').value = state.category; $('#rf-method').value = state.method; $('#rf-city').value = state.city;
  $('#rf-sort').value = state.sort; $('#rf-available').checked = state.availableOnly; $('#rf-radius').value = state.radiusKm;

  let map = null;
  let L = null;
  let layer = null;
  const mapReady = RL.map.create($('#map'), { center: [22.6, 72.2], zoom: 7 }).then((m) => {
    map = m;
    L = window.L;
    if (map) layer = L.layerGroup().addTo(map);
    return m;
  });

  function nearMode() { return state.lat !== null && state.lng !== null && !Number.isNaN(state.lat) && !Number.isNaN(state.lng); }

  function syncUrl() {
    const p = new URLSearchParams();
    if (nearMode()) {
      p.set('lat', state.lat.toFixed(5)); p.set('lng', state.lng.toFixed(5)); p.set('radiusKm', state.radiusKm);
      if (state.near) p.set('near', state.near);
    } else {
      ['q', 'city'].forEach((k) => { if (state[k]) p.set(k, state[k]); });
      if (state.sort && state.sort !== 'rating') p.set('sort', state.sort);
      if (state.page) p.set('page', state.page);
    }
    if (state.category) p.set('category', state.category);
    if (state.method) p.set('method', state.method);
    if (state.availableOnly) p.set('availableOnly', 'true');
    const qs = p.toString();
    window.history.replaceState(null, '', window.location.pathname + (qs ? '?' + qs : ''));
  }

  function setNearUi() {
    const near = nearMode();
    $('#clear-near').classList.toggle('d-none', !near);
    ['rf-q', 'rf-city', 'rf-sort'].forEach((idName) => { $('#' + idName).disabled = near; });
    $$('[data-city]').forEach((b) => b.classList.toggle('active', near && state.near === b.dataset.city));
  }

  async function drawMap(list, center) {
    await mapReady;
    if (!map) return;
    layer.clearLayers();
    const pts = [];
    if (center) {
      L.circle([center.lat, center.lng], { radius: Number(state.radiusKm) * 1000, color: '#1f5c45', weight: 1, fillOpacity: 0.05 }).addTo(layer);
      L.marker([center.lat, center.lng], { icon: RL.map.dot(L, '#2563eb'), title: 'Search centre', keyboard: false }).addTo(layer);
      pts.push([center.lat, center.lng]);
    }
    list.filter((r) => r.latitude !== null && r.longitude !== null).forEach((r) => {
      const popup = document.createElement('div');
      popup.innerHTML = `<div class="fw-semibold">${esc(r.businessName)}</div><div class="small text-muted">${esc(r.city || '')}${r.distanceKm !== null && r.distanceKm !== undefined ? ` · ${Number(r.distanceKm).toFixed(1)} km` : ''}</div>
        <div class="small">${esc((r.categories || []).map(label).join(', '))}</div>${r.startingPriceLabel ? `<div class="small">${esc(r.startingPriceLabel)}</div>` : ''}
        <a class="small" href="/repairer.html?id=${encodeURIComponent(r.id)}">View workshop</a>`;
      const m = L.marker([r.latitude, r.longitude], { icon: RL.map.dot(L, r.availability === 'AVAILABLE' ? '#1f5c45' : '#b45309'), title: r.businessName, alt: r.businessName }).bindPopup(popup).addTo(layer);
      m.repairerId = r.id;
      pts.push([r.latitude, r.longitude]);
    });
    if (pts.length > 1) map.fitBounds(pts, { padding: [30, 30], maxZoom: 14 });
    else if (pts.length === 1) map.setView(pts[0], 13);
  }

  function focusMarker(id) {
    if (!layer) return;
    layer.eachLayer((m) => { if (m.repairerId === id) { map.setView(m.getLatLng(), Math.max(map.getZoom(), 13)); m.openPopup(); } });
  }

  function renderList(list, emptyText) {
    if (!list.length) {
      $('#results').innerHTML = `<div class="card">${ui.emptyHtml({ icon: 'bi-geo', title: 'No workshops found', text: emptyText })}</div>`;
      return;
    }
    $('#results').innerHTML = list.map((r) => `<div data-rid="${esc(r.id)}">${RL.cards.repairerCard(r)}
      ${r.latitude !== null && r.longitude !== null ? `<button type="button" class="btn btn-link btn-sm px-0" data-show="${esc(r.id)}"><i class="bi bi-geo-alt me-1" aria-hidden="true"></i>Show on map${r.distanceKm !== null && r.distanceKm !== undefined ? ` · ${Number(r.distanceKm).toFixed(1)} km away` : ''}</button>` : ''}</div>`).join('');
    $$('[data-show]').forEach((b) => b.addEventListener('click', () => { focusMarker(Number(b.dataset.show)); $('#map').scrollIntoView({ behavior: 'smooth', block: 'center' }); }));
  }

  async function load() {
    syncUrl();
    setNearUi();
    ui.loader('#results', 'Finding workshops…');
    $('#pager').innerHTML = '';
    $('#result-count').textContent = '';
    try {
      if (nearMode()) {
        const res = await RL.endpoints.repairers.nearby({ lat: state.lat, lng: state.lng, radiusKm: state.radiusKm, category: state.category || null, method: state.method || null, availableOnly: state.availableOnly || null, limit: 50 });
        $('#result-count').textContent = `${res.count} workshop(s) within ${state.radiusKm} km of ${state.near || 'your location'}, nearest first`;
        renderList(res.results || [], 'Try a larger radius or fewer filters.');
        drawMap(res.results || [], { lat: state.lat, lng: state.lng });
      } else {
        const res = await RL.endpoints.repairers.search({ q: state.q || null, category: state.category || null, method: state.method || null, city: state.city || null, availableOnly: state.availableOnly || null, sort: state.sort, page: state.page, size: 10 });
        $('#result-count').textContent = `${res.totalElements} workshop(s)`;
        renderList(res.content, 'Try another city, category or search term.');
        ui.pagination('#pager', res, (n) => { state.page = n; load(); window.scrollTo(0, 0); });
        drawMap(res.content, null);
      }
    } catch (err) {
      ui.error('#results', err, load, 'Could not load repair workshops');
      drawMap([], null);
    }
  }

  form.addEventListener('submit', (ev) => {
    ev.preventDefault();
    const f = ui.formValues(form);
    Object.assign(state, { q: f.q || '', category: f.category || '', method: f.method || '', city: f.city || '', sort: f.sort || 'rating', availableOnly: $('#rf-available').checked, page: 0 });
    load();
  });
  ['rf-category', 'rf-method', 'rf-sort', 'rf-available'].forEach((idName) => $('#' + idName).addEventListener('change', () => form.requestSubmit()));
  $('#rf-radius').addEventListener('change', () => { state.radiusKm = $('#rf-radius').value; if (nearMode()) load(); });
  $$('[data-city]').forEach((b) => b.addEventListener('click', () => {
    const c = CITIES.find((x) => x.name === b.dataset.city);
    Object.assign(state, { lat: c.lat, lng: c.lng, near: c.name, page: 0 });
    load();
  }));
  $('#near-me').addEventListener('click', (ev) => {
    if (!navigator.geolocation) { ui.toast('Your browser cannot share its location. Pick a city instead.', 'warning'); return; }
    const btn = ev.currentTarget;
    btn.disabled = true;
    navigator.geolocation.getCurrentPosition((pos) => {
      btn.disabled = false;
      Object.assign(state, { lat: pos.coords.latitude, lng: pos.coords.longitude, near: '', page: 0 });
      load();
    }, (err) => {
      btn.disabled = false;
      ui.toast(err.code === 1 ? 'Location permission was denied. Pick a city instead.' : 'Could not get your location. Pick a city instead.', 'warning');
    }, { timeout: 10000, maximumAge: 300000 });
  });
  $('#clear-near').addEventListener('click', () => { Object.assign(state, { lat: null, lng: null, near: '', page: 0 }); load(); });

  load();
});
