/*
 * Transaction (payment) helpers: detail modal with the full fee breakdown, refunds and receipt.
 *   RL.transactions.showDetail(txnIdOrView, {asProvider, allowRefund, onChange})
 */
(function (window) {
  'use strict';

  const RL = window.RL = window.RL || {};
  const { esc, money, label, badge, dateTime } = RL.ui;

  function refundsHtml(list) {
    if (!list || !list.length) return '';
    return `<h3 class="h6 mt-3">Refunds</h3><div class="table-responsive"><table class="table rl-table"><thead><tr><th>Reference</th><th>Status</th><th>Reason</th><th class="num">Base refunded</th><th class="num">Fee refunded</th><th class="num">Total to customer</th><th>Date</th></tr></thead><tbody>
      ${list.map((r) => `<tr><td class="mono small">${esc(r.refundReference)}</td><td>${badge(r.status)}</td><td>${esc(label(r.reason))}</td><td class="num">${money(r.refundedBasePaise)}</td><td class="num">${money(r.refundedCustomerFeePaise)}</td><td class="num">${money(r.refundAmountPaise)}</td><td class="small">${esc(dateTime(r.completedAt))}</td></tr>`).join('')}
    </tbody></table></div>`;
  }

  function bodyHtml(tx) {
    const ref = tx.orderNumber ? `Order ${esc(tx.orderNumber)}` : (tx.repairRequestId ? `Repair request #${esc(tx.repairRequestId)}` : '—');
    return `<div class="d-flex flex-wrap justify-content-between gap-2 mb-3">
        <div><div class="small-caps">Transaction</div><div class="mono fw-semibold">${esc(tx.transactionId)}</div><div class="small text-muted">${esc(label(tx.transactionType))} · ${ref}</div></div>
        <div class="text-end">${badge(tx.status)}<div class="small text-muted mt-1">${esc(label(tx.method))}${tx.simulated ? ' · simulated' : ''}</div></div>
      </div>
      ${tx.failureReason ? `<div class="alert alert-danger small">${esc(tx.failureReason)}</div>` : ''}
      ${RL.fees.html(RL.fees.fromTransaction(tx), { title: 'Payment breakdown' })}
      <dl class="dl-grid mt-3"><dt>Attempted</dt><dd>${esc(dateTime(tx.attemptedAt))}</dd><dt>Completed</dt><dd>${esc(dateTime(tx.completedAt))}</dd>
        ${tx.payoutStatus ? `<dt>Provider payout</dt><dd>${badge(tx.payoutStatus)}</dd>` : ''}${tx.note ? `<dt>Note</dt><dd>${esc(tx.note)}</dd>` : ''}</dl>
      ${refundsHtml(tx.refunds)}`;
  }

  async function showDetail(txOrId, opts) {
    const o = opts || {};
    let tx = typeof txOrId === 'object' ? txOrId : null;
    if (!tx) {
      try {
        tx = await RL.endpoints.transactions.get(txOrId);
      } catch (err) {
        RL.ui.toastError(err, 'Could not load the payment');
        return;
      }
    }
    const canReceipt = ['SUCCESS', 'PARTIALLY_REFUNDED', 'REFUNDED'].includes(tx.status);
    const canRefund = o.allowRefund && ['SUCCESS', 'PARTIALLY_REFUNDED'].includes(tx.status);
    RL.ui.modal({
      title: 'Payment details',
      size: 'lg',
      body: bodyHtml(tx),
      footer: `${canRefund ? '<button type="button" class="btn btn-outline-danger me-auto" data-refund><i class="bi bi-arrow-counterclockwise me-1"></i>Refund</button>' : ''}
        ${canReceipt ? '<button type="button" class="btn btn-light border" data-receipt><i class="bi bi-file-earmark-text me-1"></i>Receipt</button>' : ''}
        <button type="button" class="btn btn-primary" data-bs-dismiss="modal">Close</button>`,
      onRender: (el) => {
        const r = el.querySelector('[data-receipt]');
        if (r) r.addEventListener('click', () => RL.ui.openReceipt(tx.transactionId, r));
        const f = el.querySelector('[data-refund]');
        if (f) {
          f.addEventListener('click', async () => {
            window.bootstrap.Modal.getInstance(el).hide();
            const updated = await RL.refund.open(tx);
            if (updated && o.onChange) o.onChange(updated);
          });
        }
      },
    });
  }

  RL.transactions = { showDetail, bodyHtml };
})(window);
