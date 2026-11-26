/*
 * Provider-initiated refund dialog: POST /api/transactions/{transactionId}/refunds.
 * Only the provider (seller / repairer) of a payment can refund it; the buyer gets 403 from the API.
 * Leaving the amount empty refunds the whole remaining base amount; the customer fee and commission are reversed
 * proportionally by order-service, which returns the updated TransactionView.
 */
(function (window) {
  'use strict';

  const RL = window.RL = window.RL || {};
  const { esc, money, options, ENUMS } = RL.ui;

  /**
   * Base amount still refundable. order-service's refundedAmountPaise is what went back to the CUSTOMER (base + the
   * reversed customer fee), so the refundable base comes from the refunds' refundedBasePaise, not from that total.
   */
  function refundableBase(tx) {
    const refundedBase = (tx.refunds || []).reduce((sum, r) => sum + (r.refundedBasePaise || 0), 0);
    return Math.max(0, (tx.baseAmountPaise || 0) - refundedBase);
  }

  /** tx: TransactionView (transactionId, baseAmountPaise, refundedAmountPaise, refunds[]). Resolves to updated tx or null. */
  function open(tx) {
    const refundable = refundableBase(tx);
    // one key per dialog: a double click or a retry after a network error can't refund twice
    const idempotencyKey = RL.api.newIdempotencyKey();
    return RL.ui.modal({
      title: 'Refund payment ' + tx.transactionId,
      confirmLabel: 'Issue refund',
      confirmClass: 'btn-danger',
      busyLabel: 'Refunding…',
      body: `<div class="simulated-banner mb-3"><i class="bi bi-info-circle" aria-hidden="true"></i><span>Simulated refund. No real money moves.</span></div>
        <p class="small">Base amount ${money(tx.baseAmountPaise)}, already refunded to the customer ${money(tx.refundedAmountPaise || 0)} (incl. ReLoop fee). Up to <strong>${money(refundable)}</strong> of the base amount can still be refunded. ReLoop's customer fee and your commission are reversed in proportion.</p>
        <div class="mb-3"><label class="form-label" for="rf-amount">Base amount to refund (₹)</label>
          <input class="form-control" id="rf-amount" name="amount" type="number" min="0.01" step="0.01" max="${(refundable / 100).toFixed(2)}" placeholder="Leave empty for a full refund">
          <div class="invalid-feedback">Enter an amount up to ${esc(money(refundable))}.</div></div>
        <div class="mb-3"><label class="form-label required" for="rf-reason">Reason</label>
          <select class="form-select" id="rf-reason" name="reason" required>${options(ENUMS.refundReason, '', 'Choose a reason')}</select>
          <div class="invalid-feedback">Choose a reason.</div></div>
        <div><label class="form-label" for="rf-note">Note</label><textarea class="form-control" id="rf-note" name="note" rows="2" maxlength="500"></textarea></div>`,
      onSubmit: async (form) => {
        const v = RL.ui.formValues(form);
        const body = { reason: v.reason, note: v.note || null };
        if (v.amount) body.baseAmountPaise = Math.round(Number(v.amount) * 100);
        const updated = await RL.endpoints.transactions.refund(tx.transactionId, body, idempotencyKey);
        RL.ui.toast(`Refund recorded. Payment status: ${RL.ui.label(updated.status)}.`, 'success');
        return updated;
      },
    });
  }

  RL.refund = { open, refundableBase };
})(window);
