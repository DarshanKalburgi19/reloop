/*
 * ReLoop API client + session store.
 *
 * - All URLs are relative (/api/...). The frontend dev server proxies each prefix to the right service.
 * - The JWT from product-service is kept in localStorage and sent as "Authorization: Bearer <token>" to all three
 *   services (they share the signing key).
 * - Every failure becomes an RL.ApiError {status, message, fieldErrors, body}. Nothing here ever turns a failed call
 *   into a success.
 */
(function (window) {
  'use strict';

  const RL = window.RL = window.RL || {};
  const SESSION_KEY = 'reloop.session.v1';

  class ApiError extends Error {
    constructor(status, message, body) {
      super(message);
      this.name = 'ApiError';
      this.status = status;
      this.body = body && typeof body === 'object' ? body : null;
      this.fieldErrors = (this.body && this.body.fieldErrors) || {};
    }

    /** Message plus field errors on one line, for toasts. */
    get detail() {
      const fields = Object.entries(this.fieldErrors);
      if (!fields.length) return this.message;
      return this.message + ': ' + fields.map(([k, v]) => k + ' ' + v).join('; ');
    }
  }

  const DEFAULT_MESSAGES = {
    0: 'Could not reach ReLoop. Check your connection and that the services are running.',
    400: 'Some details are not valid. Please check the highlighted fields.',
    401: 'Please sign in to continue.',
    403: 'Your account is not allowed to do this.',
    404: 'We could not find that. It may have been removed or you may not have access.',
    409: 'This conflicts with the current state. Refresh and try again.',
    422: 'The request could not be processed.',
    429: 'Too many requests. Please wait a moment and try again.',
    500: 'Something went wrong on our side. Please try again.',
    502: 'A ReLoop service is not reachable right now.',
    503: 'A ReLoop service is temporarily unavailable.',
  };

  function defaultMessage(status) {
    return DEFAULT_MESSAGES[status] || (status >= 500 ? DEFAULT_MESSAGES[500] : 'Request failed (HTTP ' + status + ').');
  }

  // ---------------------------------------------------------------- session
  const session = {
    read() {
      let raw;
      try {
        raw = window.localStorage.getItem(SESSION_KEY);
      } catch (e) {
        return null;
      }
      if (!raw) return null;
      let s;
      try {
        s = JSON.parse(raw);
      } catch (e) {
        this.clear();
        return null;
      }
      if (!s || !s.token || !s.account) { this.clear(); return null; }
      if (s.expiresAt && Date.parse(s.expiresAt) <= Date.now()) { this.clear(); return null; }
      return s;
    },
    /** Store an AuthResponse {tokenType, accessToken, expiresAt, expiresInSeconds, account}. */
    save(auth) {
      const expiresAt = auth.expiresAt
        || (auth.expiresInSeconds ? new Date(Date.now() + auth.expiresInSeconds * 1000).toISOString() : null);
      const s = { token: auth.accessToken, expiresAt, account: auth.account };
      window.localStorage.setItem(SESSION_KEY, JSON.stringify(s));
      return s;
    },
    updateAccount(account) {
      const s = this.read();
      if (!s) return;
      s.account = account;
      window.localStorage.setItem(SESSION_KEY, JSON.stringify(s));
    },
    clear() {
      try { window.localStorage.removeItem(SESSION_KEY); } catch (e) { /* storage blocked */ }
    },
    token() { const s = this.read(); return s ? s.token : null; },
    account() { const s = this.read(); return s ? s.account : null; },
    role() { const a = this.account(); return a ? a.role : null; },
    isLoggedIn() { return !!this.read(); },
  };

  // ---------------------------------------------------------------- requests
  let mockDetected = false;

  function buildUrl(path, query) {
    if (!query) return path;
    const params = new URLSearchParams();
    Object.entries(query).forEach(([k, v]) => {
      if (v === undefined || v === null || v === '') return;
      if (Array.isArray(v)) {
        if (v.length) params.set(k, v.join(','));
      } else {
        params.set(k, String(v));
      }
    });
    const qs = params.toString();
    return qs ? path + (path.includes('?') ? '&' : '?') + qs : path;
  }

  /**
   * @param {string} method
   * @param {string} path      relative URL, e.g. "/api/products"
   * @param {object} [opts]    {query, body, headers, auth (default true), raw (return {data,status,headers}),
   *                            responseType ('json' | 'text')}
   */
  async function request(method, path, opts) {
    const o = opts || {};
    const headers = Object.assign({ Accept: o.responseType === 'text' ? 'text/html, text/plain, */*' : 'application/json' }, o.headers || {});
    const init = { method, headers, credentials: 'same-origin' };
    if (o.body !== undefined) {
      headers['Content-Type'] = 'application/json';
      init.body = JSON.stringify(o.body);
    }
    const token = o.auth === false ? null : session.token();
    if (token) headers.Authorization = 'Bearer ' + token;

    let res;
    try {
      res = await window.fetch(buildUrl(path, o.query), init);
    } catch (e) {
      throw new ApiError(0, DEFAULT_MESSAGES[0], null);
    }

    if (res.headers.get('X-ReLoop-Mock') === 'true' && !mockDetected) {
      mockDetected = true;
      window.dispatchEvent(new CustomEvent('reloop:mock-backend'));
    }

    const text = await res.text();
    let data = null;
    if (text) {
      if (o.responseType === 'text') {
        data = text;
      } else {
        try { data = JSON.parse(text); } catch (e) { data = text; }
      }
    }

    if (!res.ok) {
      let body = data;
      if (typeof body === 'string') {
        try { body = JSON.parse(body); } catch (e) { body = null; }
      }
      const message = (body && typeof body === 'object' && body.message) || defaultMessage(res.status);
      const err = new ApiError(res.status, message, body);
      if (res.status === 401 && token) {
        session.clear();
        window.dispatchEvent(new CustomEvent('reloop:session-expired'));
      }
      throw err;
    }
    if (o.raw) return { data, status: res.status, headers: res.headers };
    return data;
  }

  const api = {
    request,
    get: (path, opts) => request('GET', path, opts),
    post: (path, body, opts) => request('POST', path, Object.assign({}, opts, { body: body === undefined ? {} : body })),
    put: (path, body, opts) => request('PUT', path, Object.assign({}, opts, { body })),
    patch: (path, body, opts) => request('PATCH', path, Object.assign({}, opts, { body })),
    del: (path, opts) => request('DELETE', path, opts),
    isMock: () => mockDetected,
    newIdempotencyKey() {
      if (window.crypto && window.crypto.randomUUID) return window.crypto.randomUUID();
      return 'idem-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 12);
    },
  };

  // ---------------------------------------------------------------- endpoint catalogue
  // One place that knows every path. Pages call these instead of building URLs.
  const enc = encodeURIComponent;
  const endpoints = {
    auth: {
      login: (body) => api.post('/api/auth/login', body, { auth: false }),
      registerCustomer: (body) => api.post('/api/auth/register/customer', body, { auth: false }),
      registerRepairer: (body) => api.post('/api/auth/register/repairer', body, { auth: false }),
      me: () => api.get('/api/auth/me'),
    },
    products: {
      search: (query) => api.get('/api/products', { query, auth: false }),
      mine: (query) => api.get('/api/products/mine', { query }),
      get: (id) => api.get('/api/products/' + enc(id)),
      passport: (id) => api.get('/api/products/' + enc(id) + '/passport'),
      create: (body) => api.post('/api/products', body),
      update: (id, body) => api.put('/api/products/' + enc(id), body),
      deactivate: (id) => api.del('/api/products/' + enc(id)),
    },
    fees: {
      rates: () => api.get('/api/fee-rates', { auth: false }),
    },
    orders: {
      create: (productId) => api.post('/api/orders', { productId }),
      get: (id) => api.get('/api/orders/' + enc(id)),
      list: (role) => api.get('/api/orders', { query: { role } }),
      cancel: (id, reason) => api.post('/api/orders/' + enc(id) + '/cancel', { reason: reason || null }),
    },
    payments: {
      simulate: (body, idempotencyKey) => api.post('/api/payments/simulate', body,
        { headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {} }),
      get: (txnId) => api.get('/api/payments/' + enc(txnId)),
    },
    transactions: {
      get: (txnId) => api.get('/api/transactions/' + enc(txnId)),
      receiptHtml: (txnId) => api.get('/api/transactions/' + enc(txnId) + '/receipt', { query: { format: 'html' }, responseType: 'text' }),
      ledger: (txnId) => api.get('/api/transactions/' + enc(txnId) + '/ledger'),
      refund: (txnId, body) => api.post('/api/transactions/' + enc(txnId) + '/refunds', body),
    },
    reports: {
      customerPayments: (accountId) => api.get('/api/customers/' + enc(accountId) + '/payments'),
      providerEarnings: (accountId) => api.get('/api/providers/' + enc(accountId) + '/earnings'),
      providerPayouts: (accountId) => api.get('/api/providers/' + enc(accountId) + '/payouts'),
    },
    repairers: {
      search: (query) => api.get('/api/repairers', { query, auth: false }),
      nearby: (query) => api.get('/api/repairers/nearby', { query, auth: false }),
      get: (id) => api.get('/api/repairers/' + enc(id)),
      me: () => api.get('/api/repairers/me'),
      update: (id, body) => api.put('/api/repairers/' + enc(id), body),
    },
    repairRequests: {
      create: (body) => api.post('/api/repair-requests', body),
      mine: (query) => api.get('/api/repair-requests/mine', { query }),
      incoming: (query) => api.get('/api/repair-requests/incoming', { query }),
      get: (id) => api.get('/api/repair-requests/' + enc(id)),
      history: (id) => api.get('/api/repair-requests/' + enc(id) + '/history'),
      demoImages: () => api.get('/api/repair-requests/demo-images', { auth: false }),
      accept: (id, note) => api.post('/api/repair-requests/' + enc(id) + '/accept', { note: note || null }),
      reject: (id, note) => api.post('/api/repair-requests/' + enc(id) + '/reject', { note }),
      inspect: (id, findings) => api.post('/api/repair-requests/' + enc(id) + '/inspect', { findings }),
      quote: (id, body) => api.post('/api/repair-requests/' + enc(id) + '/quote', body),
      reviseQuote: (id, body) => api.post('/api/repair-requests/' + enc(id) + '/quote/revise', body),
      progress: (id, note) => api.post('/api/repair-requests/' + enc(id) + '/progress', { note }),
      complete: (id, note) => api.post('/api/repair-requests/' + enc(id) + '/complete', { note: note || null }),
      approveQuote: (id) => api.post('/api/repair-requests/' + enc(id) + '/quote/approve', {}),
      rejectQuote: (id, note) => api.post('/api/repair-requests/' + enc(id) + '/quote/reject', { note: note || null }),
      cancel: (id, note) => api.post('/api/repair-requests/' + enc(id) + '/cancel', { note }),
    },
    inventory: {
      parts: (query) => api.get('/api/inventory/parts', { query }),
      part: (id) => api.get('/api/inventory/parts/' + enc(id)),
      create: (body) => api.post('/api/inventory/parts', body),
      update: (id, body) => api.patch('/api/inventory/parts/' + enc(id), body),
      discontinue: (id) => api.post('/api/inventory/parts/' + enc(id) + '/discontinue', {}),
      reactivate: (id) => api.post('/api/inventory/parts/' + enc(id) + '/reactivate', {}),
      stockIn: (id, body) => api.post('/api/inventory/parts/' + enc(id) + '/stock-in', body),
      use: (id, body) => api.post('/api/inventory/parts/' + enc(id) + '/use', body),
      adjust: (id, body) => api.post('/api/inventory/parts/' + enc(id) + '/adjust', body),
      partMovements: (id, query) => api.get('/api/inventory/parts/' + enc(id) + '/movements', { query }),
      movements: (query) => api.get('/api/inventory/movements', { query }),
      summary: () => api.get('/api/inventory/summary'),
      lowStock: () => api.get('/api/inventory/low-stock'),
      repairUsage: (repairRequestId) => api.get('/api/inventory/repair-requests/' + enc(repairRequestId) + '/parts'),
    },
    tracking: {
      demoLocations: () => api.get('/api/tracking/demo-locations', { auth: false }),
      book: (body) => api.post('/api/tracking/deliveries', body),
      mine: () => api.get('/api/tracking/deliveries/mine'),
      get: (tn) => api.get('/api/tracking/deliveries/' + enc(tn)),
      history: (tn) => api.get('/api/tracking/deliveries/' + enc(tn) + '/history'),
      advance: (tn) => api.post('/api/tracking/deliveries/' + enc(tn) + '/advance', {}),
      forRepair: (id) => api.get('/api/tracking/repair-requests/' + enc(id) + '/deliveries'),
      forOrder: (id) => api.get('/api/tracking/orders/' + enc(id) + '/deliveries'),
    },
  };

  RL.ApiError = ApiError;
  RL.session = session;
  RL.api = api;
  RL.endpoints = endpoints;
})(window);
