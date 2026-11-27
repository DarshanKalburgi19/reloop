#!/usr/bin/env node
/*
 * Builds the static GitHub Pages demo of the ReLoop frontend. No dependencies, Node 18+.
 *
 *   node frontend/pages-demo/build.js --out _site --base /reloop
 *
 * GitHub Pages can only host static files, so the demo differs from `node server.js --mock` in two ways:
 *
 *  1. Base path. A project site lives under https://<user>.github.io/<repo>/, but the pages use root paths such as
 *     "/assets/css/reloop.css" and "/customer/". The build copies frontend/public into --out and prefixes those
 *     paths with --base in the COPY. The source files in frontend/public are never modified.
 *  2. No server. The existing in-memory mock (frontend/dev/mock-api.js) is bundled into
 *     assets/js/pages-demo/mock.js, which runs in the browser, answers every /api/** fetch and keeps its data in
 *     localStorage so a login or order survives page changes. The UI shows its usual "mock backend" banner.
 *
 * Options: --out <dir> (default: <tmp>/reloop-pages-site), --base <path> (default: $PAGES_BASE or /reloop;
 * use "" for a user site or a custom domain).
 */
'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');

const FRONTEND = path.resolve(__dirname, '..');
const REPO = path.resolve(FRONTEND, '..');
const PUBLIC_DIR = path.join(FRONTEND, 'public');
const MOCK_SOURCE = path.join(FRONTEND, 'dev', 'mock-api.js');
const ADAPTER_SOURCE = path.join(__dirname, 'browser-mock.js');
const DEMO_IMAGES = [
  ['product-service/src/main/resources/static/demo-images/placeholders', 'demo-images/placeholders'],
  ['repair-service/src/main/resources/static/demo-images/repairs', 'demo-images/repairs'],
];
const MOCK_SCRIPT = 'assets/js/pages-demo/mock.js';
// Top-level mutable state of dev/mock-api.js that is saved to localStorage between page loads.
const STATE_ARRAYS = ['accounts', 'products', 'repairers', 'orders', 'txns', 'payouts', 'repairRequests', 'quotes',
  'parts', 'movements', 'deliveries'];

function arg(name, fallback) {
  const i = process.argv.indexOf(name);
  return i > -1 && i + 1 < process.argv.length ? process.argv[i + 1] : fallback;
}

function normaliseBase(b) {
  const s = String(b || '').trim().replace(/\/+$/, '');
  if (s === '') return '';
  if (!/^\/[A-Za-z0-9._~\-/]*$/.test(s)) throw new Error('--base must look like /reloop (got "' + b + '")');
  return s;
}

const BASE = normaliseBase(arg('--base', process.env.PAGES_BASE !== undefined ? process.env.PAGES_BASE : '/reloop'));
const OUT = path.resolve(arg('--out', path.join(os.tmpdir(), 'reloop-pages-site')));

const stats = { html: 0, js: 0, css: 0, other: 0, images: 0, attrs: 0, literals: 0 };

// ------------------------------------------------------------------ rewriting (applied to the copy only)

/** href / src / action attributes with a root path ("/x", not "//cdn") in HTML files. */
function rewriteHtml(html) {
  let out = !BASE ? html : html.replace(/\b(href|src|action|poster)="\/(?!\/)/g, (m, attr) => { stats.attrs++; return `${attr}="${BASE}/`; });
  const tag = `<script src="${BASE}/${MOCK_SCRIPT}"></script>`;
  if (!out.includes('</head>')) throw new Error('no </head> to inject the demo script into');
  out = out.replace('</head>', `  ${tag}\n</head>`);
  return out;
}

/**
 * String literals in JavaScript that are site paths: '/customer/…', '/repairer/…', '/assets/…', '/demo-images/…',
 * '/<page>.html…', home anchors '/#…', and the bare home path '/'. API paths ('/api/…') are left alone: the browser mock intercepts them.
 * A bare '/' used as a separator (split('/'), '/' + x) is not a path and is left alone too.
 */
function rewriteJs(js) {
  let out = js.replace(/(['"`])\/(customer\/|repairer\/|assets\/|demo-images\/|[A-Za-z0-9_-]+\.html\b|#)/g,
    (m, q, rest) => { stats.literals++; return `${q}${BASE}/${rest}`; });
  out = out.replace(/(?<!split\()(['"`])\/\1(?!\s*\+)/g, (m, q) => { stats.literals++; return `${q}${BASE}/${q}`; });
  return out;
}

function rewriteCss(css) {
  return css.replace(/url\((['"]?)\/(?!\/)/g, (m, q) => { stats.attrs++; return `url(${q}${BASE}/`; });
}

// ------------------------------------------------------------------ browser bundle of the mock

function buildMockBundle() {
  let mock = fs.readFileSync(MOCK_SOURCE, 'utf8');
  for (const name of STATE_ARRAYS) {
    if (!new RegExp(`^const ${name} = \\[`, 'm').test(mock)) throw new Error(`dev/mock-api.js: expected "const ${name} = [" at top level`);
  }
  if (!/^let seq = /m.test(mock) || !/^const tokens = new Map\(\);/m.test(mock) || !/^module\.exports = \{ handle \};/m.test(mock)) {
    throw new Error('dev/mock-api.js: expected "let seq", "const tokens = new Map()" and "module.exports = { handle }"');
  }
  if (BASE) mock = rewriteJs(mock); // demo image URLs in the seed data and responses
  const stateApi = `
module.exports.__state = {
  dump: () => ({ seq, tokens: Array.from(tokens.entries()), ${STATE_ARRAYS.join(', ')} }),
  load: (s) => {
    const put = (target, saved) => { if (Array.isArray(saved)) target.splice(0, target.length, ...saved); };
${STATE_ARRAYS.map((n) => `    put(${n}, s.${n});`).join('\n')}
    if (Number.isFinite(s.seq)) seq = s.seq;
    tokens.clear();
    (s.tokens || []).forEach(([k, v]) => tokens.set(k, v));
  },
};
`;
  const adapter = fs.readFileSync(ADAPTER_SOURCE, 'utf8').replace(/__RELOOP_BASE__/g, JSON.stringify(BASE));
  return `/* Generated by frontend/pages-demo/build.js for the GitHub Pages demo. Do not edit; edit the sources instead.
 * Contains frontend/dev/mock-api.js (in-memory mock of the ReLoop REST API) wrapped for the browser. */
(function () {
  'use strict';
  var randomBytes = function (n) {
    var a = new Uint8Array(n);
    globalThis.crypto.getRandomValues(a);
    a.toString = function () { return Array.from(this, function (b) { return b.toString(16).padStart(2, '0'); }).join(''); };
    return a;
  };
  var shimRequire = function (name) {
    if (name === 'crypto') return { randomBytes: randomBytes };
    return {}; // fs / path: only used to serve images from disk, which the static site serves directly
  };
  var mockModule = { exports: {} };
  (function (require, module, exports, __dirname) {
${mock}
${stateApi}
  })(shimRequire, mockModule, mockModule.exports, '/');
${adapter}
})();
`;
}

// ------------------------------------------------------------------ copy

function copyDir(src, dest) {
  fs.mkdirSync(dest, { recursive: true });
  for (const entry of fs.readdirSync(src, { withFileTypes: true })) {
    const from = path.join(src, entry.name);
    const to = path.join(dest, entry.name);
    if (entry.isDirectory()) { copyDir(from, to); continue; }
    const ext = path.extname(entry.name).toLowerCase();
    if (ext === '.html') { fs.writeFileSync(to, rewriteHtml(fs.readFileSync(from, 'utf8'))); stats.html++; }
    else if (ext === '.js') { fs.writeFileSync(to, BASE ? rewriteJs(fs.readFileSync(from, 'utf8')) : fs.readFileSync(from)); stats.js++; }
    else if (ext === '.css') { fs.writeFileSync(to, BASE ? rewriteCss(fs.readFileSync(from, 'utf8')) : fs.readFileSync(from)); stats.css++; }
    else { fs.copyFileSync(from, to); stats.other++; }
  }
}

function main() {
  if (OUT === REPO || OUT === FRONTEND || OUT === PUBLIC_DIR || PUBLIC_DIR.startsWith(OUT + path.sep)) {
    throw new Error('--out must be a separate build directory, not ' + OUT);
  }
  fs.rmSync(OUT, { recursive: true, force: true });
  copyDir(PUBLIC_DIR, OUT);
  for (const [src, dest] of DEMO_IMAGES) {
    const dir = path.join(REPO, src);
    fs.mkdirSync(path.join(OUT, dest), { recursive: true });
    for (const f of fs.readdirSync(dir).filter((n) => n.endsWith('.svg'))) {
      fs.copyFileSync(path.join(dir, f), path.join(OUT, dest, f));
      stats.images++;
    }
  }
  fs.mkdirSync(path.dirname(path.join(OUT, MOCK_SCRIPT)), { recursive: true });
  fs.writeFileSync(path.join(OUT, MOCK_SCRIPT), buildMockBundle());
  fs.writeFileSync(path.join(OUT, '.nojekyll'), ''); // serve files as-is, no Jekyll processing
  console.log(`ReLoop Pages demo built in ${OUT} (base "${BASE || '/'}")`);
  console.log(`  ${stats.html} HTML, ${stats.js} JS, ${stats.css} CSS, ${stats.other} other files, ${stats.images} demo images`);
  console.log(`  rewrote ${stats.attrs} HTML/CSS paths and ${stats.literals} JS path literals; added ${MOCK_SCRIPT}`);
}

main();
