  /*
   * Browser adapter for the GitHub Pages demo (inlined into assets/js/pages-demo/mock.js by build.js).
   * Runs inside the generated bundle, right after frontend/dev/mock-api.js, where `mockModule` holds its exports.
   *
   * - Replaces window.fetch for same-origin /api/** requests and answers them with the mock's handle(req, res, url),
   *   using small stand-ins for Node's request and response objects. Every other request uses the real fetch.
   * - Saves the mock's data to localStorage after each call and restores it on the next page load, so a demo
   *   login, listing, order or repair request survives navigation. Open any page with ?reset-demo to start over.
   */
  var BASE = __RELOOP_BASE__;
  var STORAGE_KEY = 'reloop.pagesDemo.state.v1';
  var SESSION_KEY = 'reloop.session.v1';
  var handle = mockModule.exports.handle;
  var state = mockModule.exports.__state;
  var realFetch = window.fetch ? window.fetch.bind(window) : null;

  function save() {
    try { window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state.dump())); } catch (e) { /* storage full or blocked */ }
  }

  try {
    if (/[?&]reset-demo\b/.test(window.location.search)) {
      window.localStorage.removeItem(STORAGE_KEY);
      window.localStorage.removeItem(SESSION_KEY);
    }
    var saved = window.localStorage.getItem(STORAGE_KEY);
    if (saved) state.load(JSON.parse(saved));
  } catch (e) {
    try { window.localStorage.removeItem(STORAGE_KEY); } catch (ignored) { /* storage blocked */ }
  }

  function headersToObject(h) {
    var out = {};
    if (!h) return out;
    if (typeof Headers !== 'undefined' && h instanceof Headers) {
      h.forEach(function (v, k) { out[k.toLowerCase()] = v; });
    } else if (Array.isArray(h)) {
      h.forEach(function (p) { out[String(p[0]).toLowerCase()] = String(p[1]); });
    } else {
      Object.keys(h).forEach(function (k) { out[k.toLowerCase()] = String(h[k]); });
    }
    return out;
  }

  /** Minimal stand-in for Node's IncomingMessage: method, headers and a body delivered via 'data' / 'end'. */
  function fakeRequest(method, headers, body) {
    var handlers = {};
    return {
      method: method,
      headers: headers,
      destroy: function () {},
      on: function (event, cb) {
        handlers[event] = cb;
        if (event === 'end') {
          Promise.resolve().then(function () {
            if (body && handlers.data) handlers.data(body);
            handlers.end();
          });
        }
        return this;
      },
    };
  }

  /** Minimal stand-in for Node's ServerResponse that resolves to a fetch Response. */
  function fakeResponse(resolve) {
    var status = 200;
    var headers = {};
    return {
      writeHead: function (s, h) { status = s; headers = h || {}; return this; },
      end: function (payload) {
        var noBody = status === 204 || status === 304;
        resolve(new Response(noBody ? null : (payload || ''), { status: status, headers: headers }));
      },
    };
  }

  function apiPath(url) {
    if (url.origin !== window.location.origin) return null;
    var p = url.pathname;
    if (BASE && p.indexOf(BASE + '/api/') === 0) p = p.slice(BASE.length);
    return p.indexOf('/api/') === 0 ? p : null;
  }

  window.fetch = function (input, init) {
    var isRequest = typeof Request !== 'undefined' && input instanceof Request;
    var url = new URL(isRequest ? input.url : String(input), window.location.href);
    var p = apiPath(url);
    if (p === null) {
      if (!realFetch) return Promise.reject(new TypeError('fetch is not available'));
      return realFetch(input, init);
    }
    init = init || {};
    var method = String(init.method || (isRequest ? input.method : 'GET')).toUpperCase();
    var headers = headersToObject(init.headers || (isRequest ? input.headers : null));
    var bodyPromise = init.body !== undefined && init.body !== null
      ? Promise.resolve(typeof init.body === 'string' ? init.body : String(init.body))
      : (isRequest && method !== 'GET' && method !== 'HEAD' ? input.text() : Promise.resolve(''));
    return bodyPromise.then(function (body) {
      return new Promise(function (resolve) {
        var mockUrl = new URL(p + url.search, window.location.origin);
        Promise.resolve(handle(fakeRequest(method, headers, body), fakeResponse(resolve), mockUrl)).then(save, function (err) {
          console.error('ReLoop demo mock failed', err); // eslint-disable-line no-console
          resolve(new Response(JSON.stringify({ status: 500, error: 'Internal Server Error', message: 'Demo mock error', path: p }),
            { status: 500, headers: { 'Content-Type': 'application/json', 'X-ReLoop-Mock': 'true' } }));
        });
      });
    });
  };

  window.ReLoopPagesDemo = {
    base: BASE,
    reset: function () {
      try { window.localStorage.removeItem(STORAGE_KEY); window.localStorage.removeItem(SESSION_KEY); } catch (e) { /* ignore */ }
      window.location.href = BASE + '/';
    },
  };
