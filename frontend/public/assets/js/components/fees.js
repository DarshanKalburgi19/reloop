/*
 * Fee breakdown component. Shows ALL seven figures, always, for both marketplace checkout and repair quotes:
 *
 *   1. base amount (product price / repair amount)
 *   2. customer platform fee            (added on top, paid by the customer)
 *   3. total customer payment           (= 1 + 2)
 *   4. provider gross amount            (= 1, what the seller / repairer earns before commission)
 *   5. provider commission              (deducted from 4)
 *   6. provider net earnings            (= 4 - 5)
 *   7. ReLoop total commission          (= 2 + 5)
 *
 * All values are integer paise. Adapters convert the different API shapes into one breakdown object.
 * The component never hides a line and flags any arithmetic that does not add up instead of "fixing" it.
 */
(function (window) {
  'use strict';

  const RL = window.RL = window.RL || {};
  const { esc, money, percent, toPaise } = RL.ui;

  function rateOf(part, whole) {
    if (!whole || part === null || part === undefined) return null;
    return part / whole;
  }

  /** order-service OrderView (marketplace). */
  function fromOrder(o) {
    return {
      kind: 'MARKETPLACE_PURCHASE',
      base: o.subtotalPaise,
      customerFee: o.customerFeePaise,
      customerFeeRate: o.customerFeeRate,
      total: o.totalPaise,
      providerGross: o.subtotalPaise,
      providerCommission: o.providerCommissionPaise,
      providerCommissionRate: o.providerCommissionRate,
      providerNet: o.sellerNetPaise,
      platformTotal: o.platformRevenuePaise,
      refunded: o.refundedAmountPaise || 0,
    };
  }

  /** repair-service RepairRequestResponse.quote (amounts in rupees). Rates are derived from the amounts. */
  function fromQuote(q) {
    const base = toPaise(q.repairAmount);
    const customerFee = toPaise(q.customerFee);
    const commission = toPaise(q.repairerCommission);
    return {
      kind: 'REPAIR_BOOKING',
      base,
      customerFee,
      customerFeeRate: rateOf(customerFee, base),
      rateDerived: true,
      total: toPaise(q.totalPayable),
      providerGross: base,
      providerCommission: commission,
      providerCommissionRate: rateOf(commission, base),
      providerNet: toPaise(q.repairerNet),
      platformTotal: (customerFee || 0) + (commission || 0),
    };
  }

  /** order-service TransactionView. */
  function fromTransaction(t) {
    return {
      kind: t.transactionType,
      base: t.baseAmountPaise,
      customerFee: t.customerFeePaise,
      customerFeeRate: t.customerFeeRate,
      total: t.totalChargedPaise,
      providerGross: t.baseAmountPaise,
      providerCommission: t.providerCommissionPaise,
      providerCommissionRate: t.providerCommissionRate,
      providerNet: t.providerNetPaise,
      platformTotal: t.platformRevenuePaise,
      refunded: t.refundedAmountPaise || 0,
    };
  }

  /** Same rounding as order-service FeeCalculator: paise × rate, HALF_UP to whole paise. */
  function feeOf(paise, rate) {
    const exact = Number(paise) * Number(rate);
    return Math.round(Math.round(exact * 1e6) / 1e6);
  }

  /** Estimate from GET /api/fee-rates for a base amount, before an order/quote exists. */
  function estimate(basePaise, rate, kind) {
    const customerFee = feeOf(basePaise, rate.customerFeeRate);
    const commission = feeOf(basePaise, rate.providerCommissionRate);
    return {
      kind: kind || rate.transactionType,
      estimate: true,
      base: basePaise,
      customerFee,
      customerFeeRate: rate.customerFeeRate,
      total: basePaise + customerFee,
      providerGross: basePaise,
      providerCommission: commission,
      providerCommissionRate: rate.providerCommissionRate,
      providerNet: basePaise - commission,
      platformTotal: customerFee + commission,
    };
  }

  let ratesPromise = null;
  /** Cached GET /api/fee-rates -> {MARKETPLACE_PURCHASE: FeeRateView, REPAIR_BOOKING: FeeRateView}. */
  function rates() {
    if (!ratesPromise) {
      ratesPromise = RL.endpoints.fees.rates().then((list) => {
        const map = {};
        (list || []).forEach((r) => { map[r.transactionType] = r; });
        return map;
      }).catch((err) => { ratesPromise = null; throw err; });
    }
    return ratesPromise;
  }

  function problems(b) {
    const out = [];
    const known = (v) => v !== null && v !== undefined;
    if ([b.base, b.customerFee, b.total].every(known) && b.base + b.customerFee !== b.total) {
      out.push('base amount + customer fee does not equal the total');
    }
    if ([b.providerGross, b.providerCommission, b.providerNet].every(known) && b.providerGross - b.providerCommission !== b.providerNet) {
      out.push('provider gross − commission does not equal provider net');
    }
    if ([b.customerFee, b.providerCommission, b.platformTotal].every(known) && b.customerFee + b.providerCommission !== b.platformTotal) {
      out.push('customer fee + provider commission does not equal ReLoop total commission');
    }
    return out;
  }

  function row(labelText, amount, opts) {
    const o = opts || {};
    const rate = o.rate !== null && o.rate !== undefined ? `<span class="fee-rate">(${esc(percent(o.rate))}${o.derived ? ' of base' : ''})</span>` : '';
    const sign = o.sign ? `${o.sign} ` : '';
    return `<div class="fee-row ${o.className || ''}"><span class="fee-label">${esc(labelText)}${rate}</span><span class="fee-amount">${sign}${money(amount)}</span></div>`;
  }

  /**
   * Render into target. opts: {title, providerLabel ('Seller' | 'Repairer'), baseLabel, highlight ('customer'|'provider'),
   * note (extra text), compact}
   */
  function render(target, b, opts) {
    const el = typeof target === 'string' ? document.querySelector(target) : target;
    if (!el) return;
    el.innerHTML = html(b, opts);
  }

  function html(b, opts) {
    const o = opts || {};
    const isRepair = b.kind === 'REPAIR_BOOKING';
    const provider = o.providerLabel || (isRepair ? 'Repairer' : 'Seller');
    const baseLabel = o.baseLabel || (isRepair ? 'Repair amount' : 'Product price');
    const issues = problems(b);
    const title = o.title || (b.estimate ? 'Price breakdown (estimate)' : 'Price breakdown');

    return `<section class="fee-breakdown" aria-label="${esc(title)}">
      <div class="fee-breakdown-head"><h3><i class="bi bi-receipt-cutoff me-1" aria-hidden="true"></i>${esc(title)}</h3>
        <span class="chip chip-green"><i class="bi bi-eye" aria-hidden="true"></i>All fees shown</span></div>
      <div class="fee-section">
        <div class="fee-section-title"><i class="bi bi-person" aria-hidden="true"></i>Customer pays</div>
        ${row(baseLabel, b.base)}
        ${row('ReLoop customer platform fee', b.customerFee, { rate: b.customerFeeRate, derived: b.rateDerived, sign: '+', className: 'fee-add' })}
        ${row('Total customer payment', b.total, { className: 'fee-total' })}
      </div>
      <div class="fee-section">
        <div class="fee-section-title"><i class="bi bi-shop" aria-hidden="true"></i>${esc(provider)} receives</div>
        ${row(provider + ' gross amount', b.providerGross)}
        ${row('ReLoop ' + provider.toLowerCase() + ' commission', b.providerCommission, { rate: b.providerCommissionRate, derived: b.rateDerived, sign: '−', className: 'fee-deduct' })}
        ${row(provider + ' net earnings', b.providerNet, { className: 'fee-total' })}
      </div>
      <div class="fee-section fee-platform">
        <div class="fee-section-title"><i class="bi bi-arrow-repeat" aria-hidden="true"></i>ReLoop keeps</div>
        ${row('Customer platform fee', b.customerFee)}
        ${row(provider + ' commission', b.providerCommission, { sign: '+' })}
        ${row('ReLoop total commission', b.platformTotal, { className: 'fee-total' })}
      </div>
      ${b.refunded ? `<div class="fee-section">${row('Refunded so far', b.refunded, { sign: '−', className: 'fee-deduct' })}</div>` : ''}
      ${issues.length ? `<div class="alert alert-warning m-3 mb-0 small" role="alert"><strong>These figures do not add up:</strong> ${esc(issues.join('; '))}. Please contact support before paying.</div>` : ''}
      <div class="fee-note">${b.estimate
        ? 'Estimate from the currently published ReLoop rates. The exact amounts are fixed when the order is created and shown again before you pay.'
        : 'These are the exact amounts recorded by ReLoop. Nothing is added after this step.'}${o.note ? ' ' + esc(o.note) : ''}</div>
    </section>`;
  }

  /** Small table of the published rates, for the homepage "How fees work" section. */
  function ratesTableHtml(map) {
    const rowsHtml = ['MARKETPLACE_PURCHASE', 'REPAIR_BOOKING'].filter((k) => map[k]).map((k) => {
      const r = map[k];
      return `<tr><td class="fw-semibold">${esc(RL.ui.label(k))}</td><td class="num">${esc(percent(r.customerFeeRate))}</td>
        <td class="num">${esc(percent(r.providerCommissionRate))}</td></tr>`;
    }).join('');
    return `<div class="card table-card"><div class="table-responsive"><table class="table rl-table">
      <thead><tr><th>Transaction</th><th class="num">Customer platform fee</th><th class="num">Provider commission</th></tr></thead>
      <tbody>${rowsHtml || '<tr><td colspan="3" class="text-muted">No rates published.</td></tr>'}</tbody></table></div></div>`;
  }

  RL.fees = { fromOrder, fromQuote, fromTransaction, estimate, rates, render, html, ratesTableHtml, feeOf, problems };
})(window);
