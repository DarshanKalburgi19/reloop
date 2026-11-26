/*
 * Simulated payment form -> POST /api/payments/simulate.
 *
 * SIMULATION ONLY: no card numbers, CVV, UPI IDs/PINs or bank credentials are asked for or sent. The method is a
 * label. A "demo outcome" selector lets testers simulate a decline or an abandoned payment.
 *
 *   RL.paymentForm.render(container, {amountPaise, customerFeePaise, target: {orderId} | {repairQuoteId},
 *                                     onResult(tx), title})
 * The customer must tick a box confirming the exact total before the button is enabled. A FAILED or CANCELLED
 * result is reported as such (never as success) and the form stays available for another attempt.
 */
(function (window) {
  'use strict';

  const RL = window.RL = window.RL || {};
  const { esc, money, label, ENUMS } = RL.ui;

  const ICONS = { UPI: 'bi-phone', CARD: 'bi-credit-card', NET_BANKING: 'bi-bank', WALLET: 'bi-wallet2' };
  let seq = 0;

  function render(container, o) {
    const id = 'pf' + (++seq);
    let idempotencyKey = RL.api.newIdempotencyKey();

    container.innerHTML = `<form class="card" id="${id}" novalidate><div class="card-body d-grid gap-3">
      <div class="d-flex justify-content-between align-items-center"><h3 class="h6 mb-0"><i class="bi bi-shield-lock me-1" aria-hidden="true"></i>${esc(o.title || 'Pay securely (simulated)')}</h3>
        <span class="chip"><i class="bi bi-cone-striped" aria-hidden="true"></i>Simulated</span></div>
      <div class="simulated-banner"><i class="bi bi-info-circle" aria-hidden="true"></i><span>This is a simulated payment. No real money is transferred and ReLoop never asks for card numbers, CVV, UPI PIN or bank passwords.</span></div>
      <fieldset><legend class="form-label">Payment method</legend>
        <div class="row g-2">${ENUMS.paymentMethod.map((m, i) => `<div class="col-6"><input type="radio" class="btn-check" name="method" id="${id}-${m}" value="${m}" ${i === 0 ? 'checked' : ''} required>
          <label class="btn btn-light border w-100 text-start" for="${id}-${m}"><i class="bi ${ICONS[m]} me-1" aria-hidden="true"></i>${esc(label(m))}</label></div>`).join('')}</div>
      </fieldset>
      <div><label class="form-label" for="${id}-outcome">Demo outcome</label>
        <select class="form-select" id="${id}-outcome" name="outcome">
          <option value="SUCCESS">Payment succeeds</option><option value="FAILED">Payment is declined</option><option value="CANCELLED">Customer cancels the payment</option>
        </select><div class="form-text">For testing only: choose what the simulated payment should do.</div></div>
      <div class="form-check"><input class="form-check-input" type="checkbox" id="${id}-agree" name="agree" required>
        <label class="form-check-label" for="${id}-agree">I agree to pay <strong>${money(o.amountPaise)}</strong>${o.customerFeePaise !== undefined ? `, which includes the ReLoop customer platform fee of <strong>${money(o.customerFeePaise)}</strong>` : ''}.</label>
        <div class="invalid-feedback">Please confirm the total before paying.</div></div>
      <button class="btn btn-primary btn-lg" type="submit" data-pay><i class="bi bi-lock me-1" aria-hidden="true"></i>Pay ${money(o.amountPaise)} (simulated)</button>
      <div data-result aria-live="polite"></div>
    </div></form>`;

    const form = container.querySelector('form');
    const result = form.querySelector('[data-result]');
    form.addEventListener('submit', async (ev) => {
      ev.preventDefault();
      RL.ui.clearErrors(form);
      if (!RL.ui.validate(form)) return;
      const v = RL.ui.formValues(form);
      const body = Object.assign({}, o.target, { method: v.method, outcome: v.outcome, amountPaise: o.amountPaise });
      result.innerHTML = '';
      try {
        const tx = await RL.ui.busy(form.querySelector('[data-pay]'), () => RL.endpoints.payments.simulate(body, idempotencyKey), 'Processing simulated payment…');
        if (tx.status === 'SUCCESS') {
          RL.ui.toast(`Payment ${tx.transactionId} succeeded (simulated).`, 'success');
        } else if (tx.status === 'FAILED') {
          idempotencyKey = RL.api.newIdempotencyKey();
          result.innerHTML = `<div class="alert alert-danger mb-0 small" role="alert"><strong>Payment failed.</strong> ${esc(tx.failureReason || 'The simulated payment was declined.')} Nothing was charged. You can try again.</div>`;
          RL.ui.toast('The simulated payment was declined. Nothing was charged.', 'error');
        } else if (tx.status === 'CANCELLED') {
          idempotencyKey = RL.api.newIdempotencyKey();
          result.innerHTML = `<div class="alert alert-warning mb-0 small" role="status"><strong>Payment cancelled.</strong> ${esc(tx.failureReason || '')} Nothing was charged.</div>`;
          RL.ui.toast('Payment cancelled. Nothing was charged.', 'warning');
        } else {
          result.innerHTML = `<div class="alert alert-info mb-0 small">Payment status: ${esc(label(tx.status))}.</div>`;
        }
        if (o.onResult) o.onResult(tx);
      } catch (err) {
        RL.ui.showErrors(form, err);
      }
    });
  }

  RL.paymentForm = { render };
})(window);
