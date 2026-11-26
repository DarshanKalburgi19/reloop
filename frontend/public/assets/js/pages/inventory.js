/*
 * Inventory: GET /api/inventory/{summary,low-stock,parts,movements}; POST parts, stock-in, adjust, discontinue,
 * reactivate; PATCH parts/{id}. Stock used in a repair is recorded from the repair job page ("Use a part").
 */
RL.page(async () => {
  const { $, $$, esc, money, label, badge, options, ENUMS, dateTime, number } = RL.ui;
  const ui = RL.ui;
  const E = RL.endpoints.inventory;
  let filters = {};
  let partsPage = 0;
  let movesPage = 0;
  let parts = [];
  const col = (s) => `<div class="col-6 col-xl-3">${RL.cards.statCard(s)}</div>`;
  $('#pf-category').innerHTML = options(ENUMS.partCategory, '', 'All categories');

  async function summary() {
    ui.loader('#inv-stats', 'Loading stock summary…');
    try {
      const [s, low] = await Promise.all([E.summary(), E.lowStock()]);
      $('#inv-stats').innerHTML = [
        col({ label: 'Parts', value: number(s.totalParts), icon: 'bi-box-seam', sub: `${number(s.totalUnits)} units in stock` }),
        col({ label: 'Stock value', value: s.inventoryValueDisplay || money(s.inventoryValuePaise), icon: 'bi-cash-coin', sub: 'At unit cost' }),
        col({ label: 'Low stock', value: number(s.lowStockCount), icon: 'bi-exclamation-triangle', sub: `${number(s.outOfStockCount)} out of stock` }),
        col({ label: 'Used in repairs', value: money(s.partsUsedInRepairsCostPaise || 0), icon: 'bi-tools', sub: 'Cost of parts used' }),
      ].join('');
      $('#low-stock').innerHTML = low.lowStockAlert
        ? `<div class="alert alert-warning d-flex flex-wrap align-items-center gap-2 mb-0"><i class="bi bi-exclamation-triangle-fill" aria-hidden="true"></i><span>${esc(low.alertMessage || `${low.count} part(s) are low on stock.`)}</span>
            <button type="button" class="btn btn-sm btn-warning ms-auto" id="show-low">Show low stock</button></div>`
        : '';
      if ($('#show-low')) $('#show-low').addEventListener('click', () => { $('#pf-low').checked = true; $('#part-filters').requestSubmit(); });
      if (s.valuationNote) $('#inv-stats').insertAdjacentHTML('beforeend', `<div class="col-12 small text-muted">${esc(s.valuationNote)}</div>`);
    } catch (err) {
      ui.error('#inv-stats', err, summary, 'Could not load the stock summary');
    }
  }

  async function loadParts() {
    ui.loader('#parts', 'Loading parts…');
    $('#parts-pager').innerHTML = '';
    try {
      const res = await E.parts(Object.assign({ page: partsPage, size: 20 }, filters));
      parts = res.content;
      ui.table('#parts', [
        { label: 'Part', render: (p) => `<span class="fw-semibold">${esc(p.partName)}</span><div class="small text-muted"><span class="mono">${esc(p.sku || '—')}</span> · ${esc(label(p.category))}</div>` },
        { label: 'In stock', className: 'num', render: (p) => `<span class="fw-semibold">${number(p.quantity)}</span><div class="small text-muted">alert at ${number(p.lowStockThreshold)}</div>` },
        { label: 'Unit cost', className: 'num', render: (p) => money(p.unitCostPaise) },
        { label: 'Value', className: 'num', render: (p) => money(p.stockValuePaise) },
        { label: 'Status', render: (p) => p.status === 'DISCONTINUED' ? badge('DISCONTINUED') : badge(p.stockStatus) },
        { label: '', className: 'text-end text-nowrap', render: (p) => `<div class="btn-group">
            ${p.status !== 'DISCONTINUED' ? `<button type="button" class="btn btn-sm btn-primary" data-in="${esc(p.id)}" title="Add stock"><i class="bi bi-plus-lg" aria-hidden="true"></i> Stock in</button>` : ''}
            <button type="button" class="btn btn-sm btn-light border dropdown-toggle" data-bs-toggle="dropdown" aria-expanded="false" aria-label="More actions for ${esc(p.partName)}"></button>
            <ul class="dropdown-menu dropdown-menu-end">
              <li><button class="dropdown-item" type="button" data-edit="${esc(p.id)}">Edit details</button></li>
              ${p.status !== 'DISCONTINUED' ? `<li><button class="dropdown-item" type="button" data-adjust="${esc(p.id)}">Correct stock count</button></li>` : ''}
              <li><button class="dropdown-item" type="button" data-history="${esc(p.id)}">Movement history</button></li>
              <li><hr class="dropdown-divider"></li>
              ${p.status === 'DISCONTINUED' ? `<li><button class="dropdown-item" type="button" data-reactivate="${esc(p.id)}">Reactivate</button></li>` : `<li><button class="dropdown-item text-danger" type="button" data-discontinue="${esc(p.id)}">Discontinue</button></li>`}
            </ul></div>` },
      ], parts, Object.keys(filters).length
        ? { icon: 'bi-funnel', title: 'No parts match', text: 'Try clearing the filters.' }
        : { icon: 'bi-box-seam', title: 'No parts yet', text: 'Add the spare parts you keep in stock to track quantities and get low-stock alerts.' });
      ui.pagination('#parts-pager', res, (n) => { partsPage = n; loadParts(); });
      const find = (id) => parts.find((p) => String(p.id) === String(id));
      $$('[data-in]').forEach((b) => b.addEventListener('click', () => stockIn(find(b.dataset.in))));
      $$('[data-edit]').forEach((b) => b.addEventListener('click', () => partForm(find(b.dataset.edit))));
      $$('[data-adjust]').forEach((b) => b.addEventListener('click', () => adjust(find(b.dataset.adjust))));
      $$('[data-history]').forEach((b) => b.addEventListener('click', () => history(find(b.dataset.history))));
      $$('[data-discontinue]').forEach((b) => b.addEventListener('click', () => toggle(find(b.dataset.discontinue), false)));
      $$('[data-reactivate]').forEach((b) => b.addEventListener('click', () => toggle(find(b.dataset.reactivate), true)));
    } catch (err) {
      ui.error('#parts', err, loadParts, 'Could not load parts');
    }
  }

  function movesTable(list) {
    return [
      { label: 'Date', render: (m) => `<span class="small">${esc(dateTime(m.movementDate || m.createdAt))}</span>` },
      { label: 'Part', render: (m) => `${esc(m.sparePartName)}<div class="small text-muted mono">${esc(m.sku || '')}</div>` },
      { label: 'Type', render: (m) => badge(m.movementType) },
      { label: 'Change', className: 'num', render: (m) => `<span class="${m.quantityChange < 0 ? 'text-danger' : 'text-success'} fw-semibold">${m.quantityChange > 0 ? '+' : ''}${number(m.quantityChange)}</span><div class="small text-muted">→ ${number(m.stockAfter)}</div>` },
      { label: 'Cost', className: 'num', render: (m) => money(m.totalCostPaise) },
      { label: 'Note', render: (m) => `${m.repairRequestId ? `<a class="mono small" href="/repairer/repair-request.html?id=${encodeURIComponent(m.repairRequestId)}">${esc(m.repairRequestNumber || 'Repair #' + m.repairRequestId)}</a> ` : ''}<span class="small">${esc(m.note || '')}</span>` },
    ];
  }

  async function loadMoves() {
    ui.loader('#moves', 'Loading stock movements…');
    try {
      const res = await E.movements({ page: movesPage, size: 20 });
      ui.table('#moves', movesTable(), res.content, { icon: 'bi-arrow-left-right', title: 'No stock movements yet', text: 'Stock in, corrections and parts used in repairs are recorded here.' });
      ui.pagination('#moves-pager', res, (n) => { movesPage = n; loadMoves(); });
    } catch (err) {
      ui.error('#moves', err, loadMoves, 'Could not load stock movements');
    }
  }

  function refresh() { summary(); loadParts(); if ($('#moves').innerHTML) loadMoves(); }

  function partForm(p) {
    const edit = !!p;
    const v = p || {};
    return ui.modal({
      title: edit ? `Edit ${v.partName}` : 'Add a spare part',
      size: 'lg',
      confirmLabel: edit ? 'Save changes' : 'Add part',
      body: `<div class="row g-3">
        <div class="col-md-8"><label class="form-label required" for="pt-name">Part name</label><input class="form-control" id="pt-name" name="partName" required maxlength="150" value="${esc(v.partName || '')}"><div class="invalid-feedback">Enter a part name.</div></div>
        <div class="col-md-4"><label class="form-label" for="pt-sku">SKU</label><input class="form-control mono" id="pt-sku" name="sku" maxlength="60" pattern="^[A-Za-z0-9][A-Za-z0-9._-]{0,59}$" value="${esc(v.sku || '')}"><div class="invalid-feedback">Letters, digits, . _ -</div></div>
        <div class="col-md-6"><label class="form-label required" for="pt-cat">Category</label><select class="form-select" id="pt-cat" name="category" required>${options(ENUMS.partCategory, v.category, 'Choose a category')}</select><div class="invalid-feedback">Choose a category.</div></div>
        <div class="col-md-6"><label class="form-label required" for="pt-cost">Unit cost (₹)</label><input class="form-control" id="pt-cost" name="unitCost" type="number" min="0" step="0.01" max="999999999" required value="${esc(v.unitCost !== undefined ? v.unitCost : '')}"><div class="invalid-feedback">0 or more, up to 2 decimals.</div></div>
        ${edit ? '' : '<div class="col-md-6"><label class="form-label" for="pt-qty">Opening stock</label><input class="form-control" id="pt-qty" name="quantity" type="number" min="0" step="1" value="0"></div>'}
        <div class="col-md-6"><label class="form-label" for="pt-th">Low-stock alert at</label><input class="form-control" id="pt-th" name="lowStockThreshold" type="number" min="0" max="100000" step="1" value="${esc(v.lowStockThreshold !== undefined ? v.lowStockThreshold : 2)}"><div class="form-text">Warn when stock falls to this quantity.</div></div>
        <div class="col-12"><label class="form-label" for="pt-desc">Description</label><textarea class="form-control" id="pt-desc" name="description" rows="2" maxlength="1000">${esc(v.description || '')}</textarea></div>
        ${edit ? '<div class="col-12 small text-muted">To change the quantity use Stock in or Correct stock count, so the change is recorded as a movement.</div>' : ''}
      </div>`,
      onSubmit: async (form) => {
        const f = ui.formValues(form);
        const body = { partName: f.partName, category: f.category, sku: f.sku || null, description: f.description || null, unitCost: Number(f.unitCost), lowStockThreshold: f.lowStockThreshold === '' ? null : Number(f.lowStockThreshold) };
        if (!edit) body.quantity = f.quantity === '' ? 0 : Number(f.quantity);
        const saved = edit ? await E.update(p.id, body) : await E.create(body);
        ui.toast(edit ? 'Part updated.' : 'Part added.', 'success');
        refresh();
        return saved;
      },
    });
  }

  function stockIn(p) {
    return ui.modal({
      title: `Stock in: ${p.partName}`,
      confirmLabel: 'Add stock',
      body: `<p class="small text-muted">Currently ${number(p.quantity)} in stock at ${money(p.unitCostPaise)} each.</p>
        <div class="row g-3"><div class="col-6"><label class="form-label required" for="si-qty">Quantity received</label><input class="form-control" id="si-qty" name="quantity" type="number" min="1" step="1" required><div class="invalid-feedback">At least 1.</div></div>
        <div class="col-6"><label class="form-label" for="si-cost">Unit cost (₹)</label><input class="form-control" id="si-cost" name="unitCost" type="number" min="0" step="0.01" placeholder="${esc(p.unitCost)}"><div class="form-text">Leave blank to keep the current cost.</div></div>
        <div class="col-12"><label class="form-label" for="si-note">Note</label><input class="form-control" id="si-note" name="note" maxlength="500" placeholder="Supplier, invoice number…"></div></div>`,
      onSubmit: async (form) => {
        const f = ui.formValues(form);
        const r = await E.stockIn(p.id, { quantity: Number(f.quantity), unitCost: f.unitCost === '' ? null : Number(f.unitCost), note: f.note || null });
        ui.toast(`${p.partName}: ${r.part.quantity} in stock.`, 'success');
        refresh();
        return r;
      },
    });
  }

  function adjust(p) {
    return ui.modal({
      title: `Correct stock: ${p.partName}`,
      confirmLabel: 'Save count',
      body: `<p class="small text-muted">Use this after a stock count, or for damaged or lost parts. The difference is recorded as an adjustment.</p>
        <div class="row g-3"><div class="col-6"><label class="form-label required" for="ad-qty">Counted quantity</label><input class="form-control" id="ad-qty" name="newQuantity" type="number" min="0" step="1" required value="${esc(p.quantity)}"></div>
        <div class="col-12"><label class="form-label required" for="ad-reason">Reason</label><input class="form-control" id="ad-reason" name="reason" required maxlength="500" placeholder="e.g. Monthly count, 1 damaged"><div class="invalid-feedback">A reason is required.</div></div></div>`,
      onSubmit: async (form) => {
        const f = ui.formValues(form);
        const r = await E.adjust(p.id, { newQuantity: Number(f.newQuantity), reason: f.reason });
        ui.toast('Stock count saved.', 'success');
        refresh();
        return r;
      },
    });
  }

  async function history(p) {
    let res;
    try { res = await E.partMovements(p.id, { size: 50 }); } catch (err) { ui.toastError(err, 'Could not load the movement history'); return; }
    const holder = document.createElement('div');
    ui.table(holder, movesTable().filter((c) => c.label !== 'Part'), res.content, { icon: 'bi-arrow-left-right', title: 'No movements yet' });
    ui.modal({ title: `Movements: ${p.partName}`, size: 'lg', body: holder.innerHTML, footer: '<button type="button" class="btn btn-light" data-bs-dismiss="modal">Close</button>' });
  }

  async function toggle(p, reactivate) {
    const ok = await ui.confirm({
      title: reactivate ? `Reactivate ${p.partName}?` : `Discontinue ${p.partName}?`,
      message: reactivate ? 'The part becomes available for stock in and repairs again.' : 'The part is hidden from the list and cannot be used in repairs. Its history is kept and you can reactivate it later.',
      confirmLabel: reactivate ? 'Reactivate' : 'Discontinue',
      confirmClass: reactivate ? 'btn-primary' : 'btn-danger',
    });
    if (!ok) return;
    try {
      await (reactivate ? E.reactivate(p.id) : E.discontinue(p.id));
      ui.toast(reactivate ? 'Part reactivated.' : 'Part discontinued.', 'success');
      refresh();
    } catch (err) { ui.toastError(err); }
  }

  $('#add-part').addEventListener('click', () => partForm(null));
  $('#part-filters').addEventListener('submit', (ev) => {
    ev.preventDefault();
    const f = ui.formValues(ev.target);
    filters = {};
    if (f.q) filters.q = f.q;
    if (f.category) filters.category = f.category;
    if ($('#pf-low').checked) filters.lowStockOnly = true;
    if ($('#pf-disc').checked) filters.includeDiscontinued = true;
    partsPage = 0;
    loadParts();
  });
  $('#moves-tab').addEventListener('click', () => loadMoves());
  summary();
  loadParts();
});
