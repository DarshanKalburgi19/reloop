/*
 * Profile (customer and repairer): GET /api/auth/me. Account details are read-only because product-service has no
 * account-update endpoint yet. Repairers also get their business profile editor (RL.repairerProfile, loaded only on
 * /repairer/profile.html).
 */
RL.page(async ({ role }) => {
  const { $, esc, label, badge, dateTime } = RL.ui;
  const ui = RL.ui;
  const host = $('#profile');

  async function load() {
    ui.loader(host, 'Loading your profile…');
    let me;
    try {
      me = await RL.endpoints.auth.me();
      RL.session.updateAccount(me);
    } catch (err) {
      ui.error(host, err, load, 'Could not load your profile');
      return;
    }
    const c = me.customer;
    const r = me.repairer;
    const w = r && r.workshop;
    host.innerHTML = `<div class="row g-4">
      <div class="col-lg-${role === 'REPAIRER' ? '12' : '7'}">
        <div class="card"><div class="card-header d-flex justify-content-between align-items-center"><span>Account</span>${badge(me.status)}</div><div class="card-body">
          <div class="d-flex align-items-center gap-3 mb-3"><span class="rl-avatar" style="width:56px;height:56px;font-size:1.2rem">${esc(ui.initials(me.name))}</span>
            <div><div class="h5 mb-0">${esc(me.name)}</div><div class="text-muted small">${esc(label(me.role))} account · member since ${esc(dateTime(me.createdAt))}</div></div></div>
          <dl class="dl-grid">
            <dt>Email</dt><dd>${esc(me.email)}</dd>
            <dt>Phone</dt><dd>${esc(me.phone || '—')}</dd>
            <dt>Account ID</dt><dd class="mono">${esc(me.id)}</dd>
            ${c ? `<dt>Address</dt><dd>${esc(c.address || '—')}</dd><dt>City</dt><dd>${esc(c.city || '—')}</dd><dt>State</dt><dd>${esc(c.state || '—')}</dd><dt>PIN code</dt><dd>${esc(c.pincode || '—')}</dd>` : ''}
            ${r ? `<dt>Verification</dt><dd>${badge(r.verificationStatus)}</dd>` : ''}
            ${w ? `<dt>Registered workshop</dt><dd>${esc(w.workshopName)}</dd><dt>Registered hours</dt><dd>${esc(w.serviceHours || '—')}</dd>` : ''}
          </dl>
          <div class="alert alert-light border small mb-0 mt-3"><i class="bi bi-info-circle me-1" aria-hidden="true"></i>Changing your name, email, phone or password is not available in the app yet.${role === 'REPAIRER' ? ' Your public workshop details below can be edited.' : ''}</div>
        </div></div>
      </div>
      ${role === 'CUSTOMER' ? `<div class="col-lg-5"><div class="card"><div class="card-header">Shortcuts</div><div class="list-group list-group-flush">
        <a class="list-group-item list-group-item-action" href="/customer/purchases.html"><i class="bi bi-receipt me-2"></i>My purchases</a>
        <a class="list-group-item list-group-item-action" href="/customer/listings.html"><i class="bi bi-tags me-2"></i>My listings</a>
        <a class="list-group-item list-group-item-action" href="/customer/repairs.html"><i class="bi bi-tools me-2"></i>My repair requests</a>
        <a class="list-group-item list-group-item-action" href="/customer/payments.html"><i class="bi bi-credit-card-2-front me-2"></i>Payment history</a>
        <button type="button" class="list-group-item list-group-item-action text-danger" data-signout-profile><i class="bi bi-box-arrow-right me-2"></i>Sign out</button></div></div></div>` : ''}
    </div>
    ${role === 'REPAIRER' ? '<div class="mt-4" id="business-profile"></div>' : ''}`;
    const so = $('[data-signout-profile]', host);
    if (so) so.addEventListener('click', RL.layout.signOut);
    if (role === 'REPAIRER' && RL.repairerProfile) RL.repairerProfile.mountEditor($('#business-profile'), ['business']);
  }
  load();
});
