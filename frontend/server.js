#!/usr/bin/env node
/*
 * ReLoop frontend dev server. No dependencies, Node 18+.
 *
 *  - Serves the static site in ./public.
 *  - Proxies API calls to the three Spring Boot services by path prefix, so the browser only ever talks to this
 *    origin. The pages use relative URLs, there is no CORS to configure, and the JWT stays an ordinary
 *    Authorization header.
 *  - Never proxies /api/internal/** (those endpoints need the service-to-service key and must not be public).
 *
 *    node server.js            proxy to the real services (PRODUCT_API / ORDER_API / REPAIR_API)
 *    node server.js --mock     answer /api/** from dev/mock-api.js (in-memory, for UI work without Java).
 *                              Every mock response carries "X-ReLoop-Mock: true" and the UI shows a banner.
 *
 * Env: PORT (8080), HOST (0.0.0.0), PRODUCT_API (http://localhost:8081), ORDER_API (http://localhost:8082),
 *      REPAIR_API (http://localhost:8083).
 */
'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');
const { URL } = require('url');

const PORT = Number(process.env.PORT || 8080);
const HOST = process.env.HOST || '0.0.0.0';
const MOCK = process.argv.includes('--mock') || process.env.RELOOP_MOCK === 'true';
const PUBLIC_DIR = path.join(__dirname, 'public');

const UPSTREAMS = {
  product: process.env.PRODUCT_API || 'http://localhost:8081',
  order: process.env.ORDER_API || 'http://localhost:8082',
  repair: process.env.REPAIR_API || 'http://localhost:8083',
};

// Order matters only for readability: matching is by whole path segment, so /api/customer never catches
// /api/customers and /api/repairer never catches /api/repairers.
const ROUTES = [
  ['/api/auth', 'product'],
  ['/api/products', 'product'],
  ['/api/customer', 'product'],
  ['/api/repairer', 'product'],
  ['/demo-images/placeholders', 'product'],
  ['/api/orders', 'order'],
  ['/api/payments', 'order'],
  ['/api/transactions', 'order'],
  ['/api/customers', 'order'],
  ['/api/providers', 'order'],
  ['/api/fee-rates', 'order'],
  ['/api/repairers', 'repair'],
  ['/api/repair-requests', 'repair'],
  ['/api/repairs', 'repair'],
  ['/api/tracking', 'repair'],
  ['/api/inventory', 'repair'],
  ['/demo-images/repairs', 'repair'],
];

const SERVICE_NAMES = { product: 'product-service', order: 'order-service', repair: 'repair-service' };

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.txt': 'text/plain; charset=utf-8',
};

const CSP = [
  "default-src 'self'",
  "script-src 'self' https://cdn.jsdelivr.net https://unpkg.com",
  "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://unpkg.com https://fonts.googleapis.com",
  "font-src 'self' https://cdn.jsdelivr.net https://fonts.gstatic.com",
  "img-src 'self' data: blob: https:",
  "connect-src 'self'",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
].join('; ');

function matchRoute(pathname) {
  for (const [prefix, target] of ROUTES) {
    if (pathname === prefix || pathname.startsWith(prefix + '/')) return target;
  }
  return null;
}

function sendJson(res, status, body, extraHeaders) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
    'Cache-Control': 'no-store',
    ...(extraHeaders || {}),
  });
  res.end(payload);
}

function apiError(status, error, message, pathname) {
  return { timestamp: new Date().toISOString(), status, error, message, path: pathname };
}

function proxy(req, res, target, url) {
  const base = new URL(UPSTREAMS[target]);
  const headers = { ...req.headers };
  delete headers.host;
  delete headers.connection;
  delete headers['x-internal-api-key'];
  headers['x-forwarded-host'] = req.headers.host || '';
  headers['x-forwarded-proto'] = req.headers['x-forwarded-proto'] || 'http';

  const upstream = http.request({
    protocol: base.protocol,
    hostname: base.hostname,
    port: base.port,
    method: req.method,
    path: url.pathname + url.search,
    headers,
    timeout: 30000,
  }, (up) => {
    const out = { ...up.headers };
    delete out.connection;
    res.writeHead(up.statusCode || 502, out);
    up.pipe(res);
  });

  upstream.on('timeout', () => upstream.destroy(new Error('timeout')));
  upstream.on('error', (err) => {
    if (res.headersSent) { res.destroy(); return; }
    const name = SERVICE_NAMES[target];
    sendJson(res, 502, apiError(502, 'Bad Gateway',
      `${name} is not reachable at ${UPSTREAMS[target]} (${err.code || err.message}). Start the service and try again.`,
      url.pathname));
  });
  req.pipe(upstream);
}

function serveStatic(req, res, url) {
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    sendJson(res, 405, apiError(405, 'Method Not Allowed', 'Static files are read-only', url.pathname));
    return;
  }
  let pathname;
  try {
    pathname = decodeURIComponent(url.pathname);
  } catch (e) {
    res.writeHead(400); res.end('Bad request'); return;
  }
  if (pathname.endsWith('/')) pathname += 'index.html';
  const file = path.normalize(path.join(PUBLIC_DIR, pathname));
  if (!file.startsWith(PUBLIC_DIR + path.sep)) { res.writeHead(403); res.end('Forbidden'); return; }

  fs.stat(file, (err, stat) => {
    if (!err && stat.isDirectory()) {
      res.writeHead(301, { Location: url.pathname + '/' + url.search }); res.end(); return;
    }
    if (err || !stat.isFile()) {
      const notFound = path.join(PUBLIC_DIR, '404.html');
      fs.readFile(notFound, (e2, body) => {
        res.writeHead(404, { 'Content-Type': MIME['.html'], 'Content-Security-Policy': CSP });
        res.end(e2 ? 'Not found' : body);
      });
      return;
    }
    const ext = path.extname(file).toLowerCase();
    res.writeHead(200, {
      'Content-Type': MIME[ext] || 'application/octet-stream',
      'Content-Length': stat.size,
      'Cache-Control': ext === '.html' ? 'no-cache' : 'public, max-age=300',
      'X-Content-Type-Options': 'nosniff',
      'Referrer-Policy': 'same-origin',
      ...(ext === '.html' ? { 'Content-Security-Policy': CSP } : {}),
    });
    if (req.method === 'HEAD') { res.end(); return; }
    fs.createReadStream(file).pipe(res);
  });
}

const mock = MOCK ? require('./dev/mock-api.js') : null;

const server = http.createServer((req, res) => {
  let url;
  try {
    url = new URL(req.url, 'http://localhost');
  } catch (e) {
    res.writeHead(400); res.end('Bad request'); return;
  }

  if (url.pathname === '/api/internal' || url.pathname.startsWith('/api/internal/')) {
    sendJson(res, 404, apiError(404, 'Not Found', 'Internal endpoints are not exposed to the browser', url.pathname));
    return;
  }
  if (url.pathname === '/healthz') {
    sendJson(res, 200, { status: 'UP', mode: MOCK ? 'mock' : 'proxy', upstreams: MOCK ? null : UPSTREAMS });
    return;
  }

  const target = matchRoute(url.pathname);
  if (target) {
    if (mock) { mock.handle(req, res, url); return; }
    proxy(req, res, target, url);
    return;
  }
  if (url.pathname.startsWith('/api/')) {
    sendJson(res, 404, apiError(404, 'Not Found', 'No ReLoop API matches ' + url.pathname, url.pathname));
    return;
  }
  serveStatic(req, res, url);
});

function start() {
  server.listen(PORT, HOST, () => {
  console.log(`ReLoop frontend on http://${HOST}:${PORT}`);
  if (MOCK) {
    console.log('MOCK MODE: /api/** is answered by dev/mock-api.js (in-memory demo data, not the real services).');
  } else {
    console.log('Proxying: product ' + UPSTREAMS.product + ' | order ' + UPSTREAMS.order + ' | repair ' + UPSTREAMS.repair);
  }
  });
}

if (require.main === module) start();

module.exports = { matchRoute, ROUTES, server };
