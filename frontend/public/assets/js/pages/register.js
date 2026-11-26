/*
 * Registration (two separate pages share this script):
 *   register-customer.html -> POST /api/auth/register/customer
 *   register-repairer.html -> POST /api/auth/register/repairer   (form has data-role="REPAIRER")
 * On success the returned JWT is stored and the user lands on their dashboard. Errors map onto the fields.
 */
RL.page(async () => {
  const { $, $$ } = RL.ui;
  const ui = RL.ui;
  const form = $('#register-form');
  const isRepairer = form.dataset.role === 'REPAIRER';

  $$('[data-toggle-password]').forEach((b) => b.addEventListener('click', () => {
    const input = b.parentElement.querySelector('input');
    const show = input.type === 'password';
    input.type = show ? 'text' : 'password';
    b.setAttribute('aria-label', show ? 'Hide password' : 'Show password');
    b.innerHTML = `<i class="bi ${show ? 'bi-eye-slash' : 'bi-eye'}" aria-hidden="true"></i>`;
  }));

  const confirmInput = $('#confirmPassword');
  const syncConfirm = () => confirmInput.setCustomValidity(confirmInput.value && confirmInput.value !== $('#password').value ? 'mismatch' : '');
  confirmInput.addEventListener('input', syncConfirm);
  $('#password').addEventListener('input', syncConfirm);

  if (isRepairer) {
    $('#repairMethods').innerHTML = ui.ENUMS.repairMethod.map((m) => `<div class="form-check">
      <input class="form-check-input" type="checkbox" name="repairMethods" value="${m}" id="rm-${m}"${m === 'IN_PERSON' ? ' checked' : ''}>
      <label class="form-check-label" for="rm-${m}">${ui.esc(ui.label(m))}</label></div>`).join('');
    RL.locationPicker.attach({ map: $('#pick-map'), lat: $('#latitude'), lng: $('#longitude'), button: $('#use-location') });
  }

  form.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    ui.clearErrors(form);
    syncConfirm();
    if (!ui.validate(form)) {
      if (confirmInput.validity.customError) ui.formAlert(form, 'The two passwords do not match.');
      return;
    }
    const v = ui.formValues(form);
    let body;
    let call;
    if (isRepairer) {
      const expertise = String(v.repairExpertise || '').split(',').map((s) => s.trim()).filter(Boolean);
      const methods = ui.checkedValues(form, 'repairMethods');
      if (!methods.length) { ui.showErrors(form, { message: 'Choose at least one repair method.', fieldErrors: { repairMethods: 'choose at least one' } }); return; }
      if (expertise.length > 10 || expertise.some((e) => e.length > 40)) {
        ui.showErrors(form, { message: 'Please check your expertise list.', fieldErrors: { repairExpertise: 'up to 10 items, each up to 40 characters' } });
        return;
      }
      body = {
        name: v.name, email: v.email, phone: v.phone, password: v.password,
        workshopName: v.workshopName, repairExpertise: expertise, workshopAddress: v.workshopAddress,
        city: v.city, pincode: v.pincode || null, latitude: Number(v.latitude), longitude: Number(v.longitude),
        serviceHours: v.serviceHours, repairMethods: methods,
      };
      call = RL.endpoints.auth.registerRepairer;
    } else {
      body = {
        name: v.name, email: v.email, phone: v.phone, password: v.password,
        address: v.address, city: v.city, pincode: v.pincode, state: v.state || null,
      };
      call = RL.endpoints.auth.registerCustomer;
    }
    try {
      const auth = await ui.busy(form.querySelector('[type=submit]'), () => call(body), 'Creating your account…');
      RL.session.save(auth);
      ui.flash(isRepairer
        ? 'Workshop registered. Add your services and opening hours so customers can find you.'
        : `Welcome to ReLoop, ${auth.account.name}!`, 'success');
      window.location.href = isRepairer ? '/repairer/' : '/customer/';
    } catch (err) {
      ui.showErrors(form, err);
    }
  });
});
