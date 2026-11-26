#!/usr/bin/env node
/*
 * Dependency-free frontend checks (run by `npm test` / `npm run check` and CI):
 *   1. every .js file under public/ and dev/ and server.js parses (node --check)
 *   2. every local <script src>, <link href>, <img src> and <a href> in the HTML pages points at an existing file
 *   3. every page that uses RL.page loads core/api.js, core/ui.js and core/layout.js, in that order, before page code
 *   4. no inline <script> blocks or inline event handlers (the dev server's CSP forbids them)
 *   5. CDN assets carry Subresource Integrity hashes
 *   6. links inside JavaScript template strings to *.html pages point at existing pages
 *   7. the dev server routes every /api prefix used by RL.endpoints to a service
 */
'use strict';

const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const ROOT = path.resolve(__dirname, '..');
const PUBLIC = path.join(ROOT, 'public');
const problems = [];
let checks = 0;

function walk(dir, ext, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name.startsWith('.')) continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, ext, out);
    else if (entry.name.endsWith(ext)) out.push(full);
  }
  return out;
}
const rel = (f) => path.relative(ROOT, f);
const fail = (file, msg) => problems.push(`${rel(file)}: ${msg}`);

// 1. syntax
const jsFiles = walk(PUBLIC, '.js').concat(walk(path.join(ROOT, 'dev'), '.js'), walk(path.join(ROOT, 'scripts'), '.js'), [path.join(ROOT, 'server.js')]);
for (const f of jsFiles) {
  checks++;
  try {
    execFileSync(process.execPath, ['--check', f], { stdio: 'pipe' });
  } catch (e) {
    fail(f, 'syntax error\n' + String(e.stderr || e.message));
  }
}

// 2-5. HTML pages
const htmlFiles = walk(PUBLIC, '.html');
const pageExists = (urlPath, fromFile) => {
  const clean = urlPath.split('#')[0].split('?')[0];
  if (!clean) return true;
  let target = clean.startsWith('/') ? path.join(PUBLIC, clean) : path.join(path.dirname(fromFile), clean);
  if (clean.endsWith('/')) target = path.join(target, 'index.html');
  return fs.existsSync(target);
};
const isLocal = (u) => u && !/^(https?:|mailto:|tel:|data:|#|javascript:)/i.test(u) && !u.startsWith('//');
// Paths served by the backend services through the dev-server proxy, not by public/.
const PROXIED = ['/demo-images/', '/api/'];

for (const f of htmlFiles) {
  const html = fs.readFileSync(f, 'utf8');
  checks++;
  for (const m of html.matchAll(/<(script|img|link|a)\b[^>]*?\s(src|href)="([^"]*)"/gi)) {
    const [, tag, , url] = m;
    if (!isLocal(url) || PROXIED.some((p) => url.startsWith(p))) continue;
    if (!pageExists(url, f)) fail(f, `<${tag}> points at missing ${url}`);
  }
  if (/<script(?![^>]*\bsrc=)[^>]*>/i.test(html)) fail(f, 'inline <script> block (blocked by CSP)');
  if (/\son[a-z]+="/i.test(html)) fail(f, 'inline event handler attribute (blocked by CSP)');
  for (const m of html.matchAll(/<(?:script\b[^>]*\ssrc|link\b[^>]*rel="stylesheet"[^>]*\shref)="(https:[^"]+)"[^>]*>/gi)) {
    if (/fonts\.googleapis\.com/.test(m[1])) continue; // CSS varies per user agent; SRI not possible
    if (!/\bintegrity="sha(256|384|512)-/.test(m[0])) fail(f, `CDN asset without integrity: ${m[1]}`);
  }
  const scripts = [...html.matchAll(/<script src="\/assets\/js\/([^"]+)"/g)].map((m) => m[1]);
  if (scripts.some((s) => s.startsWith('pages/'))) {
    const order = ['core/api.js', 'core/ui.js', 'core/layout.js'].map((s) => scripts.indexOf(s));
    if (order.some((i) => i < 0)) fail(f, 'missing one of core/api.js, core/ui.js, core/layout.js');
    else if (!(order[0] < order[1] && order[1] < order[2])) fail(f, 'core scripts must load in the order api, ui, layout');
    const firstPage = scripts.findIndex((s) => s.startsWith('pages/'));
    if (firstPage < order[2]) fail(f, 'page script loads before core/layout.js');
    const lastComponent = scripts.reduce((acc, s, i) => (s.startsWith('components/') ? i : acc), -1);
    if (lastComponent > firstPage) fail(f, 'component script loads after the page script');
  }
  if (!/<html lang="en"/.test(html)) fail(f, 'missing <html lang="en">');
  if (!/<title>[^<]+<\/title>/.test(html)) fail(f, 'missing <title>');
  if (!/<main\b[^>]*id="main"/.test(html)) fail(f, 'missing <main id="main"> (skip-link target)');
}

// 6. links in JS templates
for (const f of walk(path.join(PUBLIC, 'assets', 'js'), '.js')) {
  checks++;
  const src = fs.readFileSync(f, 'utf8');
  for (const m of src.matchAll(/['"`](\/[a-z0-9/-]*\.html)/gi)) {
    if (!pageExists(m[1], f)) fail(f, `links to missing page ${m[1]}`);
  }
  for (const m of src.matchAll(/['"`](\/(?:customer|repairer)\/)['"`?]/g)) {
    if (!pageExists(m[1], f)) fail(f, `links to missing page ${m[1]}`);
  }
}

// 7. every API prefix used by the client is routed by the dev server
const { matchRoute } = require(path.join(ROOT, 'server.js'));
const apiSrc = fs.readFileSync(path.join(PUBLIC, 'assets', 'js', 'core', 'api.js'), 'utf8');
const prefixes = new Set([...apiSrc.matchAll(/'(\/api\/[a-z-]+)/g)].map((m) => m[1]));
for (const p of prefixes) {
  checks++;
  if (!matchRoute(p + '/x')) problems.push(`server.js: no proxy route for ${p} (used in core/api.js)`);
}

if (problems.length) {
  console.error(`Frontend check FAILED (${problems.length} problem(s) in ${checks} checks):\n- ` + problems.join('\n- '));
  process.exit(1);
}
console.log(`Frontend check passed: ${jsFiles.length} JS files, ${htmlFiles.length} HTML pages, ${checks} checks.`);
