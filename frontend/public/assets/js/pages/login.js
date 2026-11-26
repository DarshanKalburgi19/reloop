/* Sign in: POST /api/auth/login, store the JWT, then go to ?next= (same-site only) or the role's dashboard. */
RL.page(async ({ account }) => {
  const { $, esc, ui } = { $: RL.ui.$, esc: RL.ui.esc, ui: RL.ui };
  const next = new URLSearchParams(window.location.search).get('next');

  function destination(acc) {
    const home = RL.layout.HOME[acc.role] || '/';
    const target = RL.layout.safeNext(next, home);
    // Never send a customer into the repairer area (or the reverse) just because ?next= pointed there.
    if (target.startsWith('/customer/') && acc.role !== 'CUSTOMER') return home;
    if (target.startsWith('/repairer/') && acc.role !== 'REPAIRER') return home;
    return target;
  }

  if (account) {
    $('#login-form').insertAdjacentHTML('beforebegin', `<div class="alert alert-success small">You are already signed in as <strong>${esc(account.name)}</strong>. <a href="${esc(destination(account))}">Continue</a> or sign in with another account below.</div>`);
  }

  RL.ui.$$('[data-toggle-password]').forEach((b) => b.addEventListener('click', () => {
    const input = b.parentElement.querySelector('input');
    const show = input.type === 'password';
    input.type = show ? 'text' : 'password';
    b.setAttribute('aria-label', show ? 'Hide password' : 'Show password');
    b.innerHTML = `<i class="bi ${show ? 'bi-eye-slash' : 'bi-eye'}" aria-hidden="true"></i>`;
  }));

  const demos = [
    ['Asha Patel', 'Customer · Ahmedabad', 'customer.demo@reloop.local', 'ReLoopDemo#Cust2026'],
    ['Karan Desai', 'Customer & seller · Surat', 'karan.demo@reloop.local', 'ReLoopDemo#Karan2026'],
    ['Nisha Trivedi', 'Customer & seller · Vadodara', 'nisha.demo@reloop.local', 'ReLoopDemo#Nisha2026'],
    ['Ravi Mehta', 'Repairer · Mehta Fix-It Workshop', 'repairer.demo@reloop.local', 'ReLoopDemo#Rep2026'],
  ];
  $('#demo-list').innerHTML = demos.map((d, i) => `<button type="button" class="btn btn-light border text-start d-flex justify-content-between align-items-center" data-demo="${i}">
    <span><span class="fw-semibold">${esc(d[0])}</span><span class="d-block text-muted small">${esc(d[1])}</span></span><span class="small text-muted mono">${esc(d[2])}</span></button>`).join('');
  RL.ui.$$('[data-demo]').forEach((b) => b.addEventListener('click', () => {
    const d = demos[Number(b.dataset.demo)];
    $('#email').value = d[2];
    $('#password').value = d[3];
    $('#login-submit').focus();
  }));

  const form = $('#login-form');
  form.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    ui.clearErrors(form);
    if (!ui.validate(form)) return;
    const v = ui.formValues(form);
    try {
      const auth = await ui.busy($('#login-submit'), () => RL.endpoints.auth.login({ email: v.email, password: v.password }), 'Signing in…');
      RL.session.save(auth);
      ui.flash(`Welcome back, ${auth.account.name}.`, 'success');
      window.location.href = destination(auth.account);
    } catch (err) {
      ui.showErrors(form, err);
    }
  });
});
