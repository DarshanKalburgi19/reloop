/*
 * ReLoop UI helpers: escaping, formatting, labels, status badges, toasts, loaders, empty/error states,
 * tables, pagination, form helpers and promise-based dialogs. Depends on Bootstrap 5's JS bundle for modals.
 */
(function (window, document) {
  'use strict';

  const RL = window.RL = window.RL || {};

  // ---------------------------------------------------------------- escaping & DOM
  const ESC = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;', '`': '&#96;' };
  function esc(value) {
    if (value === null || value === undefined) return '';
    return String(value).replace(/[&<>"'`]/g, (c) => ESC[c]);
  }
  /** Only same-origin relative paths and https URLs are allowed into href/src attributes. */
  function safeUrl(url) {
    if (typeof url !== 'string') return '';
    const u = url.trim();
    if (/^\/(?!\/)/.test(u) || /^https:\/\//i.test(u)) return u;
    return '';
  }
  const $ = (sel, root) => (root || document).querySelector(sel);
  const $$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));
  function param(name) { return new URLSearchParams(window.location.search).get(name); }

  // ---------------------------------------------------------------- formatting
  const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const inr0 = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 0, maximumFractionDigits: 2 });
  const num = new Intl.NumberFormat('en-IN');

  /** Paise (integer) -> "₹1,234.50". */
  function money(paise) {
    if (paise === null || paise === undefined || paise === '' || Number.isNaN(Number(paise))) return '—';
    return inr.format(Number(paise) / 100);
  }
  /** Rupees (BigDecimal from the API) -> "₹1,234.50". */
  function rupees(value, compact) {
    if (value === null || value === undefined || value === '' || Number.isNaN(Number(value))) return '—';
    return (compact ? inr0 : inr).format(Number(value));
  }
  /** Rupees -> paise without float drift (e.g. 19.99 -> 1999). */
  function toPaise(rupeesValue) {
    if (rupeesValue === null || rupeesValue === undefined || rupeesValue === '') return null;
    return Math.round(Number(rupeesValue) * 100);
  }
  function percent(rate, digits) {
    if (rate === null || rate === undefined || rate === '' || Number.isNaN(Number(rate))) return '';
    const p = Number(rate) * 100;
    return (Number.isInteger(p) ? p.toFixed(0) : p.toFixed(digits === undefined ? 2 : digits).replace(/0+$/, '').replace(/\.$/, '')) + '%';
  }
  function number(n) { return n === null || n === undefined ? '—' : num.format(n); }

  // The API sends LocalDateTime strings without a zone ("2026-11-25T10:15:30"); they are server-local (IST).
  function parseDate(value) {
    if (!value) return null;
    const d = new Date(value);
    return Number.isNaN(d.getTime()) ? null : d;
  }
  function date(value) {
    const d = parseDate(value);
    return d ? d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '—';
  }
  function dateTime(value) {
    const d = parseDate(value);
    return d ? d.toLocaleString('en-IN', { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' }) : '—';
  }
  function relative(value) {
    const d = parseDate(value);
    if (!d) return '—';
    const diff = (Date.now() - d.getTime()) / 1000;
    const abs = Math.abs(diff);
    const fmt = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });
    if (abs < 60) return fmt.format(-Math.round(diff), 'second');
    if (abs < 3600) return fmt.format(-Math.round(diff / 60), 'minute');
    if (abs < 86400) return fmt.format(-Math.round(diff / 3600), 'hour');
    if (abs < 86400 * 30) return fmt.format(-Math.round(diff / 86400), 'day');
    return date(value);
  }
  function initials(name) {
    return String(name || '?').trim().split(/\s+/).slice(0, 2).map((p) => p[0] || '').join('').toUpperCase() || '?';
  }

  // ---------------------------------------------------------------- labels
  const LABELS = {
    HOME_KITCHEN: 'Home & kitchen', SPORTS_TOYS: 'Sports & toys', LIKE_NEW: 'Like new', VERY_GOOD: 'Very good',
    HOME_APPLIANCE: 'Home appliance', TV: 'TV', IN_PERSON: 'In person (visit workshop)', SEND_ITEM: 'Send item (courier)',
    PICKUP_DROPOFF: 'Pickup & drop-off', STARTING_FROM: 'Starting from', PENDING_PAYMENT: 'Pending payment',
    QUOTE_APPROVED: 'Quote approved', QUOTE_REJECTED: 'Quote rejected', AWAITING_PAYMENT: 'Awaiting payment',
    IN_PROGRESS: 'In progress', NET_BANKING: 'Net banking', UPI: 'UPI', MARKETPLACE_PURCHASE: 'Marketplace purchase',
    REPAIR_BOOKING: 'Repair booking', PARTIALLY_REFUNDED: 'Partially refunded', SCREEN_DISPLAY: 'Screen & display',
    CHARGING_POWER: 'Charging & power', CIRCUIT_BOARD: 'Circuit board', STORAGE_MEMORY: 'Storage & memory',
    KEYBOARD_INPUT: 'Keyboard & input', CABLES_CONNECTORS: 'Cables & connectors', STOCK_IN: 'Stock in',
    USED_IN_REPAIR: 'Used in repair', IN_STOCK: 'In stock', LOW_STOCK: 'Low stock', OUT_OF_STOCK: 'Out of stock',
    STORE_TO_CUSTOMER: 'Seller → buyer', CUSTOMER_TO_REPAIRER: 'Customer → workshop', REPAIRER_TO_CUSTOMER: 'Workshop → customer',
    PICKED_UP: 'Picked up', IN_TRANSIT: 'In transit', NEAR_DESTINATION: 'Near destination', ON_HOLD: 'On hold',
    PARTIALLY_REVERSED: 'Partially reversed', DISPUTED_ORDER: 'Disputed order', CUSTOMER_REQUEST: 'Customer request',
    ITEM_NOT_AS_DESCRIBED: 'Item not as described', REPAIR_NOT_COMPLETED: 'Repair not completed', ORDER_CANCELLED: 'Order cancelled',
  };
  function label(value) {
    if (value === null || value === undefined || value === '') return '—';
    const v = String(value);
    if (LABELS[v]) return LABELS[v];
    const s = v.replace(/_/g, ' ').toLowerCase();
    return s.charAt(0).toUpperCase() + s.slice(1);
  }

  const ENUMS = {
    productCategory: ['ELECTRONICS', 'FURNITURE', 'FASHION', 'BOOKS', 'HOME_KITCHEN', 'SPORTS_TOYS', 'APPLIANCES', 'OTHER'],
    productCondition: ['LIKE_NEW', 'VERY_GOOD', 'GOOD', 'FAIR'],
    repairCategory: ['MOBILE', 'LAPTOP', 'TABLET', 'TV', 'HOME_APPLIANCE', 'BICYCLE', 'ELECTRICAL', 'OTHER'],
    repairMethod: ['IN_PERSON', 'SEND_ITEM', 'PICKUP_DROPOFF'],
    priceType: ['FIXED', 'STARTING_FROM'],
    availability: ['AVAILABLE', 'BUSY', 'UNAVAILABLE'],
    days: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'],
    paymentMethod: ['UPI', 'CARD', 'NET_BANKING', 'WALLET'],
    partCategory: ['SCREEN_DISPLAY', 'BATTERY', 'CHARGING_POWER', 'CAMERA', 'AUDIO', 'CIRCUIT_BOARD', 'STORAGE_MEMORY',
      'KEYBOARD_INPUT', 'CABLES_CONNECTORS', 'MECHANICAL', 'CONSUMABLES', 'OTHER'],
    refundReason: ['CUSTOMER_REQUEST', 'ITEM_NOT_AS_DESCRIBED', 'DISPUTED_ORDER', 'REPAIR_NOT_COMPLETED', 'ORDER_CANCELLED', 'OTHER'],
  };
  function options(values, selected, placeholder) {
    const sel = Array.isArray(selected) ? selected.map(String) : [String(selected === undefined || selected === null ? '' : selected)];
    let html = placeholder !== undefined ? `<option value="">${esc(placeholder)}</option>` : '';
    values.forEach((v) => {
      const value = typeof v === 'object' ? v.value : v;
      const text = typeof v === 'object' ? v.label : label(v);
      html += `<option value="${esc(value)}"${sel.includes(String(value)) ? ' selected' : ''}>${esc(text)}</option>`;
    });
    return html;
  }

  // ---------------------------------------------------------------- status badges
  const TONES = {
    success: ['ACTIVE', 'AVAILABLE', 'CONFIRMED', 'SUCCESS', 'PAID', 'COMPLETED', 'DELIVERED', 'IN_STOCK', 'VERIFIED', 'APPROVED', 'RECORDED'],
    warning: ['PENDING_PAYMENT', 'PENDING', 'AWAITING_PAYMENT', 'QUOTED', 'LOW_STOCK', 'BUSY', 'RESERVED', 'ON_HOLD', 'SENT', 'INITIATED',
      'PARTIALLY_REFUNDED', 'PARTIALLY_REVERSED'],
    danger: ['FAILED', 'CANCELLED', 'REJECTED', 'QUOTE_REJECTED', 'OUT_OF_STOCK', 'UNAVAILABLE', 'SUSPENDED', 'REVERSED', 'REFUNDED'],
    info: ['REQUESTED', 'ACCEPTED', 'INSPECTED', 'QUOTE_APPROVED', 'IN_PROGRESS', 'BOOKED', 'PICKED_UP', 'IN_TRANSIT', 'NEAR_DESTINATION'],
  };
  function tone(status) {
    const s = String(status || '').toUpperCase();
    for (const [t, list] of Object.entries(TONES)) if (list.includes(s)) return t;
    return 'neutral';
  }
  function badge(status, text) {
    if (!status) return '<span class="status-badge status-neutral">Unknown</span>';
    return `<span class="status-badge status-${tone(status)}">${esc(text || label(status))}</span>`;
  }

  /**
   * Payment state of a repair request (repair-service paymentStatus, reported by order-service). PAID is already
   * implied by the workflow status, so only the states that need attention get a badge.
   */
  const PAYMENT_TEXT = { FAILED: 'Last payment failed', CANCELLED: 'Payment cancelled', PARTIALLY_REFUNDED: 'Partly refunded', REFUNDED: 'Refunded' };
  function paymentBadge(paymentStatus) {
    return PAYMENT_TEXT[paymentStatus] ? ' ' + badge(paymentStatus, PAYMENT_TEXT[paymentStatus]) : '';
  }

  // ---------------------------------------------------------------- live refresh (MVP polling)
  /**
   * Keeps a dashboard in sync with what the other party does, without websockets: calls fetcher() every
   * `interval` ms and apply(data) only when the response changed (JSON fingerprint), so nothing flickers.
   * - runs only while the tab is visible; refreshes at once when the tab becomes visible again;
   * - waits while the user is busy (a dialog is open or a form field has focus), so input is never lost;
   * - backs off on errors (interval x2, x4 … up to maxInterval) and resets after the next success;
   * - stops on 401/403 (the session handling takes over);
   * - shows its state in #live-status (created in the page header when missing).
   * Returns { stop, refresh, mark(data) }: call mark() with the data the page loaded itself.
   */
  function poll(fetcher, apply, opts) {
    const o = Object.assign({ interval: 10000, maxInterval: 60000 }, opts);
    let last = null; let timer = null; let delay = o.interval; let inFlight = false; let stopped = false; let failures = 0;
    const fingerprint = (data) => { try { return JSON.stringify(data); } catch (e) { return null; } };
    const busy = () => {
      if (document.querySelector('.modal.show, dialog[open]')) return true;
      const el = document.activeElement;
      return !!(el && /^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName) && el.closest('form'));
    };
    function indicator(state, retryMs) {
      let el = document.getElementById('live-status');
      if (!el) {
        const header = document.querySelector('.rl-page-header');
        if (!header) return;
        el = document.createElement('div');
        el.id = 'live-status';
        el.className = 'rl-live small text-muted';
        header.appendChild(el);
      }
      const time = new Date().toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
      el.dataset.state = state;
      el.title = 'This page refreshes itself every ' + Math.round(o.interval / 1000) + ' s while it is open.';
      el.innerHTML = state === 'ok'
        ? `<i class="bi bi-arrow-repeat" aria-hidden="true"></i> Live · updated ${esc(time)}`
        : state === 'error'
          ? `<i class="bi bi-wifi-off" aria-hidden="true"></i> Connection problem · retrying in ${Math.round(retryMs / 1000)} s`
          : '<i class="bi bi-pause-circle" aria-hidden="true"></i> Auto-refresh stopped';
    }
    function schedule(ms) { clearTimeout(timer); if (!stopped) timer = setTimeout(tick, ms); }
    async function tick(force) {
      if (stopped || document.visibilityState === 'hidden') return; // resumed by visibilitychange
      if (inFlight || (force !== true && busy())) { schedule(o.interval); return; }
      inFlight = true;
      try {
        const data = await fetcher();
        failures = 0; delay = o.interval;
        const fp = fingerprint(data);
        if (fp === null || fp !== last) { last = fp; await apply(data); }
        indicator('ok');
      } catch (err) {
        if (err && (err.status === 401 || err.status === 403)) { stop(); indicator('stopped'); return; }
        failures += 1;
        delay = Math.min(o.maxInterval, o.interval * Math.pow(2, failures));
        indicator('error', delay);
      } finally {
        inFlight = false;
      }
      schedule(delay);
    }
    function onVisibility() { if (document.visibilityState === 'visible') tick(); else clearTimeout(timer); }
    function stop() { stopped = true; clearTimeout(timer); document.removeEventListener('visibilitychange', onVisibility); }
    document.addEventListener('visibilitychange', onVisibility);
    schedule(o.interval);
    return {
      stop,
      refresh: () => tick(true),
      mark(data) { last = fingerprint(data); indicator('ok'); },
    };
  }

  // ---------------------------------------------------------------- toasts
  function toastStack() {
    let stack = document.getElementById('rl-toast-stack');
    if (!stack) {
      stack = document.createElement('div');
      stack.id = 'rl-toast-stack';
      stack.className = 'rl-toast-stack';
      stack.setAttribute('aria-live', 'polite');
      stack.setAttribute('aria-atomic', 'false');
      document.body.appendChild(stack);
    }
    return stack;
  }
  const TOAST_ICONS = { success: 'bi-check-circle-fill', error: 'bi-exclamation-octagon-fill', warning: 'bi-exclamation-triangle-fill', info: 'bi-info-circle-fill' };
  /** type: success | error | warning | info. Errors stay until dismissed for 10 s so they can be read. */
  function toast(message, type, opts) {
    const t = type || 'success';
    const el = document.createElement('div');
    el.className = 'rl-toast ' + t;
    el.setAttribute('role', t === 'error' ? 'alert' : 'status');
    el.innerHTML = `<i class="bi ${TOAST_ICONS[t] || TOAST_ICONS.info}" aria-hidden="true"></i>
      <div class="flex-grow-1">${opts && opts.title ? `<div class="fw-semibold">${esc(opts.title)}</div>` : ''}<div>${esc(message)}</div></div>
      <button type="button" class="btn-close" aria-label="Dismiss"></button>`;
    el.querySelector('.btn-close').addEventListener('click', () => el.remove());
    toastStack().appendChild(el);
    const ms = (opts && opts.duration) || (t === 'error' ? 10000 : 5000);
    window.setTimeout(() => el.remove(), ms);
    return el;
  }
  function toastError(err, fallback) {
    const msg = err instanceof RL.ApiError ? err.detail : (err && err.message) || fallback || 'Something went wrong.';
    return toast(msg, 'error', { title: fallback && err instanceof RL.ApiError ? fallback : undefined });
  }
  /** Show a toast on the next page (after a redirect). */
  function flash(message, type) {
    try { window.sessionStorage.setItem('reloop.flash', JSON.stringify({ message, type: type || 'success' })); } catch (e) { /* ignore */ }
  }
  function consumeFlash() {
    try {
      const raw = window.sessionStorage.getItem('reloop.flash');
      if (!raw) return;
      window.sessionStorage.removeItem('reloop.flash');
      const f = JSON.parse(raw);
      toast(f.message, f.type);
    } catch (e) { /* ignore */ }
  }

  // ---------------------------------------------------------------- loader / empty / error
  function loader(target, text) {
    const el = typeof target === 'string' ? $(target) : target;
    if (el) el.innerHTML = `<div class="rl-loader" role="status"><span class="spinner-border" aria-hidden="true"></span><span>${esc(text || 'Loading…')}</span></div>`;
  }
  function emptyHtml(o) {
    const opt = o || {};
    const action = opt.actionHref
      ? `<a class="btn btn-primary btn-sm" href="${esc(safeUrl(opt.actionHref))}">${esc(opt.actionLabel || 'Get started')}</a>` : '';
    return `<div class="rl-empty"><div class="rl-empty-icon"><i class="bi ${esc(opt.icon || 'bi-inbox')}" aria-hidden="true"></i></div>
      <h3>${esc(opt.title || 'Nothing here yet')}</h3>${opt.text ? `<p>${esc(opt.text)}</p>` : ''}${action}</div>`;
  }
  function empty(target, o) {
    const el = typeof target === 'string' ? $(target) : target;
    if (el) el.innerHTML = emptyHtml(o);
  }
  function errorHtml(err, title) {
    const msg = err instanceof RL.ApiError ? err.message : (err && err.message) || 'Something went wrong.';
    const status = err instanceof RL.ApiError && err.status ? ` <span class="small opacity-75">(HTTP ${esc(err.status)})</span>` : '';
    return `<div class="rl-error" role="alert"><i class="bi bi-exclamation-octagon" aria-hidden="true"></i><div class="flex-grow-1">
      <div class="fw-semibold">${esc(title || 'Could not load this section')}${status}</div><div class="small">${esc(msg)}</div>
      <button type="button" class="btn btn-sm btn-outline-danger mt-2 d-none" data-retry>Try again</button></div></div>`;
  }
  /** Render an error state. If retry is given, a "Try again" button calls it. */
  function error(target, err, retry, title) {
    const el = typeof target === 'string' ? $(target) : target;
    if (!el) return;
    el.innerHTML = errorHtml(err, title);
    if (retry) {
      const b = el.querySelector('[data-retry]');
      b.classList.remove('d-none');
      b.addEventListener('click', retry);
    }
  }

  /** Disable a button and show a spinner while a promise runs. Returns the promise result. */
  async function busy(button, fn, busyText) {
    if (!button) return fn();
    const original = button.innerHTML;
    button.disabled = true;
    button.setAttribute('aria-busy', 'true');
    button.innerHTML = `<span class="spinner-border me-1" aria-hidden="true"></span>${esc(busyText || 'Please wait…')}`;
    try {
      return await fn();
    } finally {
      button.disabled = false;
      button.removeAttribute('aria-busy');
      button.innerHTML = original;
    }
  }

  // ---------------------------------------------------------------- tables & pagination
  /**
   * columns: [{label, render(row) -> html, className, headClass}]
   * Renders a full card with a responsive table, or the empty state when rows is empty.
   */
  function table(target, columns, rows, emptyOpts) {
    const el = typeof target === 'string' ? $(target) : target;
    if (!el) return;
    if (!rows || !rows.length) { el.innerHTML = `<div class="card">${emptyHtml(emptyOpts)}</div>`; return; }
    const head = columns.map((c) => `<th scope="col" class="${esc(c.headClass || c.className || '')}">${esc(c.label)}</th>`).join('');
    const body = rows.map((r, i) => `<tr>${columns.map((c) => `<td class="${esc(c.className || '')}">${c.render(r, i)}</td>`).join('')}</tr>`).join('');
    el.innerHTML = `<div class="card table-card"><div class="table-responsive"><table class="table rl-table">
      <thead><tr>${head}</tr></thead><tbody>${body}</tbody></table></div></div>`;
  }
  /** Spring-style page {page, totalPages, totalElements, first, last}. onPage(newPage) is called on click. */
  function pagination(target, page, onPage) {
    const el = typeof target === 'string' ? $(target) : target;
    if (!el) return;
    if (!page || page.totalPages <= 1) { el.innerHTML = ''; return; }
    el.innerHTML = `<nav class="d-flex align-items-center justify-content-between gap-2 mt-3" aria-label="Pagination">
      <span class="small text-muted">Page ${page.page + 1} of ${page.totalPages} · ${number(page.totalElements)} results</span>
      <div class="btn-group">
        <button type="button" class="btn btn-light btn-sm" data-p="${page.page - 1}" ${page.first ? 'disabled' : ''}><i class="bi bi-chevron-left"></i> Previous</button>
        <button type="button" class="btn btn-light btn-sm" data-p="${page.page + 1}" ${page.last ? 'disabled' : ''}>Next <i class="bi bi-chevron-right"></i></button>
      </div></nav>`;
    $$('[data-p]', el).forEach((b) => b.addEventListener('click', () => onPage(Number(b.dataset.p))));
  }

  // ---------------------------------------------------------------- forms
  function formValues(form) {
    const out = {};
    new FormData(form).forEach((value, key) => {
      if (out[key] === undefined) out[key] = typeof value === 'string' ? value.trim() : value;
      else if (Array.isArray(out[key])) out[key].push(value);
      else out[key] = [out[key], value];
    });
    return out;
  }
  function checkedValues(form, name) {
    return $$(`input[name="${name}"]:checked`, form).map((i) => i.value);
  }
  function clearErrors(form) {
    $$('.is-invalid', form).forEach((el) => el.classList.remove('is-invalid'));
    $$('.invalid-feedback[data-generated]', form).forEach((el) => el.remove());
    const alert = $('[data-form-alert]', form);
    if (alert) { alert.className = 'd-none'; alert.innerHTML = ''; }
  }
  function formAlert(form, message, type) {
    let alert = $('[data-form-alert]', form);
    if (!alert) {
      alert = document.createElement('div');
      alert.setAttribute('data-form-alert', '');
      form.prepend(alert);
    }
    alert.className = `alert alert-${type || 'danger'} d-flex gap-2 align-items-start`;
    alert.setAttribute('role', 'alert');
    alert.innerHTML = `<i class="bi ${type === 'success' ? 'bi-check-circle' : 'bi-exclamation-triangle'}" aria-hidden="true"></i><div>${esc(message)}</div>`;
  }
  /**
   * Map an ApiError's fieldErrors onto inputs with a matching name (or data-field). "services[0].price" style keys
   * fall back to their first segment. Unmatched errors are listed in the form alert, so nothing is silently dropped.
   */
  function showErrors(form, err) {
    clearErrors(form);
    const unmatched = [];
    const fields = (err && err.fieldErrors) || {};
    Object.entries(fields).forEach(([field, msg]) => {
      const base = field.split(/[.[]/)[0];
      const input = form.querySelector(`[name="${CSS.escape(field)}"], [data-field="${CSS.escape(field)}"]`)
        || form.querySelector(`[name="${CSS.escape(base)}"], [data-field="${CSS.escape(base)}"]`);
      if (!input) { unmatched.push(field + ': ' + msg); return; }
      input.classList.add('is-invalid');
      const fb = document.createElement('div');
      fb.className = 'invalid-feedback';
      fb.setAttribute('data-generated', '');
      fb.textContent = msg;
      const anchor = input.closest('.input-group') || input.closest('[data-field-wrap]') || input;
      anchor.insertAdjacentElement('afterend', fb);
      if (anchor !== input) fb.style.display = 'block';
    });
    let message = err instanceof RL.ApiError ? err.message : (err && err.message) || 'Something went wrong.';
    if (unmatched.length) message += ' — ' + unmatched.join('; ');
    formAlert(form, message, 'danger');
    const first = form.querySelector('.is-invalid');
    if (first) first.focus({ preventScroll: false });
  }
  /** Client-side validation using the browser's constraint API; returns true when valid. */
  function validate(form) {
    if (form.checkValidity()) return true;
    form.classList.add('was-validated');
    const first = form.querySelector(':invalid');
    if (first) first.focus();
    return false;
  }

  // ---------------------------------------------------------------- dialogs
  function modalShell(id, title, body, footer, size) {
    const wrap = document.createElement('div');
    wrap.innerHTML = `<div class="modal fade" id="${esc(id)}" tabindex="-1" aria-labelledby="${esc(id)}-title" aria-hidden="true">
      <div class="modal-dialog modal-dialog-centered modal-dialog-scrollable ${size ? 'modal-' + esc(size) : ''}"><div class="modal-content">
        <div class="modal-header"><h2 class="modal-title h5" id="${esc(id)}-title">${esc(title)}</h2>
          <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button></div>
        <div class="modal-body">${body}</div>
        ${footer ? `<div class="modal-footer">${footer}</div>` : ''}
      </div></div></div>`;
    const el = wrap.firstElementChild;
    document.body.appendChild(el);
    return el;
  }
  let modalSeq = 0;
  /**
   * Generic modal. body is trusted HTML built by the caller (escape API data first).
   * onSubmit(form, modalEl) may return a promise; if it throws, the error is shown inside the modal and the modal
   * stays open. Resolves with onSubmit's result, or null if dismissed.
   */
  function modal(o) {
    return new Promise((resolve) => {
      const id = 'rl-modal-' + (++modalSeq);
      const footer = o.footer !== undefined ? o.footer : `<button type="button" class="btn btn-light" data-bs-dismiss="modal">${esc(o.cancelLabel || 'Cancel')}</button>
        ${o.onSubmit ? `<button type="submit" form="${id}-form" class="btn ${esc(o.confirmClass || 'btn-primary')}" data-submit>${esc(o.confirmLabel || 'Save')}</button>` : ''}`;
      const body = o.onSubmit ? `<form id="${id}-form" novalidate>${o.body}</form>` : o.body;
      const el = modalShell(id, o.title, body, footer, o.size);
      const bs = new window.bootstrap.Modal(el);
      let result = null;
      el.addEventListener('hidden.bs.modal', () => { bs.dispose(); el.remove(); resolve(result); });
      if (o.onSubmit) {
        const form = el.querySelector('form');
        form.addEventListener('submit', async (ev) => {
          ev.preventDefault();
          clearErrors(form);
          if (!validate(form)) return;
          const btn = el.querySelector('[data-submit]');
          try {
            result = await busy(btn, () => o.onSubmit(form, el), o.busyLabel || 'Saving…');
            if (result === undefined) result = true;
            bs.hide();
          } catch (err) {
            result = null;
            showErrors(form, err);
          }
        });
      }
      if (o.onShow) el.addEventListener('shown.bs.modal', () => o.onShow(el), { once: true });
      if (o.onRender) o.onRender(el);
      bs.show();
    });
  }
  /** Yes/no confirmation. Resolves true/false. */
  function confirm(o) {
    return new Promise((resolve) => {
      const id = 'rl-confirm-' + (++modalSeq);
      const el = modalShell(id, o.title || 'Are you sure?', `<p class="mb-0">${esc(o.message || '')}</p>${o.html || ''}`,
        `<button type="button" class="btn btn-light" data-bs-dismiss="modal">${esc(o.cancelLabel || 'Cancel')}</button>
         <button type="button" class="btn ${esc(o.confirmClass || 'btn-primary')}" data-ok>${esc(o.confirmLabel || 'Confirm')}</button>`, o.size);
      const bs = new window.bootstrap.Modal(el);
      let ok = false;
      el.querySelector('[data-ok]').addEventListener('click', () => { ok = true; bs.hide(); });
      el.addEventListener('hidden.bs.modal', () => { bs.dispose(); el.remove(); resolve(ok); });
      bs.show();
    });
  }
  /**
   * Ask for a note, then run action(note). Errors keep the dialog open. Resolves with action's result or null.
   */
  function prompt(o) {
    const minLength = o.minLength || 0;
    return modal({
      title: o.title,
      confirmLabel: o.confirmLabel || 'Submit',
      confirmClass: o.confirmClass,
      busyLabel: o.busyLabel,
      body: `${o.message ? `<p>${esc(o.message)}</p>` : ''}
        <label class="form-label ${o.required ? 'required' : ''}" for="rl-prompt-note">${esc(o.label || 'Note')}</label>
        <textarea class="form-control" id="rl-prompt-note" name="${esc(o.field || 'note')}" rows="${o.rows || 3}"
          ${o.required ? 'required' : ''} ${minLength ? `minlength="${minLength}"` : ''} maxlength="${o.maxLength || 2000}"
          placeholder="${esc(o.placeholder || '')}"></textarea>
        <div class="invalid-feedback">${esc(o.invalidText || (minLength ? `Please write at least ${minLength} characters.` : 'This is required.'))}</div>
        ${o.help ? `<div class="form-text">${esc(o.help)}</div>` : ''}`,
      onShow: (el) => el.querySelector('textarea').focus(),
      onSubmit: (form) => o.action(form.querySelector('textarea').value.trim()),
    });
  }

  /** Fetch an authenticated HTML receipt and open it in a new tab (the receipt endpoint needs the JWT). */
  async function openReceipt(txnId, button) {
    const win = window.open('', '_blank');
    try {
      const html = await busy(button, () => RL.endpoints.transactions.receiptHtml(txnId), 'Opening…');
      const blob = new Blob([html], { type: 'text/html' });
      const url = URL.createObjectURL(blob);
      if (win) win.location = url; else window.location.href = url;
      window.setTimeout(() => URL.revokeObjectURL(url), 60000);
    } catch (err) {
      if (win) win.close();
      toastError(err, 'Could not open the receipt');
    }
  }

  // Broken images fall back to data-fallback once (CSP forbids inline onerror handlers).
  document.addEventListener('error', (ev) => {
    const img = ev.target;
    if (img && img.tagName === 'IMG' && img.dataset.fallback && !img.dataset.fellBack) {
      img.dataset.fellBack = '1';
      img.src = img.dataset.fallback;
    }
  }, true);

  RL.ui = {
    esc, safeUrl, $, $$, param, money, rupees, toPaise, percent, number, date, dateTime, relative, initials,
    label, ENUMS, options, tone, badge, toast, toastError, flash, consumeFlash, loader, empty, emptyHtml, error, errorHtml,
    busy, table, pagination, formValues, checkedValues, clearErrors, formAlert, showErrors, validate, modal, confirm,
    prompt, openReceipt, paymentBadge, poll,
  };
  RL.poll = poll;
})(window, document);
