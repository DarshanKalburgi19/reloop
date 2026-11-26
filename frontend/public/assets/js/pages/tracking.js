/*
 * Tracking page: GET /api/tracking/deliveries/mine (legs you are a party of) + detail of the selected one (?tn=).
 * All deliveries are simulated; see RL.tracking for the wording rules.
 */
RL.page(async () => {
  const { $, $$ } = RL.ui;
  const ui = RL.ui;
  let legs = [];
  let active = ui.param('tn');

  function select(tn) {
    active = tn;
    const url = new URL(window.location.href);
    url.searchParams.set('tn', tn);
    window.history.replaceState(null, '', url.pathname + url.search);
    $$('[data-tn]').forEach((b) => b.classList.toggle('active', b.dataset.tn === tn));
    showDetail();
  }

  async function showDetail() {
    if (!active) {
      $('#detail').innerHTML = legs.length ? `<div class="card">${ui.emptyHtml({ icon: 'bi-hand-index', title: 'Select a delivery', text: 'Choose a delivery on the left to see its simulated route and checkpoints.' })}</div>` : '';
      return;
    }
    ui.loader('#detail', 'Loading delivery…');
    try {
      const d = await RL.endpoints.tracking.get(active);
      RL.tracking.renderDetail($('#detail'), d, { onChange: () => loadList(true) });
    } catch (err) {
      ui.error('#detail', err, showDetail, 'Could not load this delivery');
    }
  }

  async function loadList(keepDetail) {
    if (!keepDetail) ui.loader('#legs', 'Loading deliveries…');
    try {
      legs = await RL.endpoints.tracking.mine();
    } catch (err) {
      ui.error('#legs', err, () => loadList(false), 'Could not load your deliveries');
      if (active && !keepDetail) showDetail();
      return;
    }
    if (!legs.length) {
      $('#legs').innerHTML = `<div class="card">${ui.emptyHtml({ icon: 'bi-truck', title: 'No deliveries yet', text: 'Simulated deliveries appear here once a seller ships your order or a repair pickup is booked.' })}</div>`;
    } else {
      $('#legs').innerHTML = `<div class="small-caps mb-2">${legs.length} deliver${legs.length === 1 ? 'y' : 'ies'}</div>` + RL.tracking.legsHtml(legs, active);
      $$('[data-tn]').forEach((b) => b.addEventListener('click', () => select(b.dataset.tn)));
    }
    if (!keepDetail) {
      if (!active && legs.length) active = legs[0].trackingNumber;
      showDetail();
    }
  }
  loadList(false);
});
