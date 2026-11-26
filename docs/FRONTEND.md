# ReLoop frontend

Static HTML, CSS, Bootstrap 5.3 and plain JavaScript. There is no build step and no npm dependencies. A small Node
dev server (`frontend/server.js`) serves the pages and proxies `/api/**` to the three services, so the browser only
ever talks to one origin. That means no CORS, and JWTs never go to a third-party host.

## Running it

```bash
# 1. Start the services (see README / docs/PHASE1.md). Demo accounts need reloop.seed.enabled=true.
#    product-service :8081, order-service :8082, repair-service :8083

# 2. Start the frontend (Node 18+)
cd frontend
npm start                 # http://localhost:8080, proxies to the services above
```

| Variable | Default | Meaning |
|---|---|---|
| `PORT` / `HOST` | `8080` / `0.0.0.0` | where the dev server listens |
| `PRODUCT_API` | `http://localhost:8081` | product-service base URL |
| `ORDER_API` | `http://localhost:8082` | order-service base URL |
| `REPAIR_API` | `http://localhost:8083` | repair-service base URL |

If a service is down, its calls get a `502` in the standard error format
(`{status, error, message, path, fieldErrors}`). The page then shows "product-service is not reachable at …" with a
**Try again** button. It never shows invented data.

### Mock backend (UI work without Java)

```bash
npm run start:mock        # same as: node server.js --mock   (or RELOOP_MOCK=1)
```

`frontend/dev/mock-api.js` is an **in-memory, dev-only** imitation of the three services' REST contracts. It
includes the four demo accounts, eight products, three workshops, spare parts, fees of 2% + 2%, simulated payments,
refunds, repair workflow and tracking. Every mock response carries `X-ReLoop-Mock: true`, and the UI shows an amber
"Mock backend" bar while it is active. Data resets on restart. Never deploy with `--mock`.

### Checks

```bash
npm test                  # node scripts/check.js
```

The check runs on CI (`Frontend checks` job), followed by a dev-server smoke test against the mock. It covers:

- `node --check` on every JS file.
- Every local `src`/`href` in the HTML resolves.
- `.html` links inside JS templates resolve.
- Core scripts load in the order `api → ui → layout → components → page`.
- No inline `<script>` blocks or `on*=` handlers (blocked by CSP).
- SRI hashes on CDN assets.
- Every `/api` prefix used by the client is routed by the dev server.

## Pages

| Path | Who | What it does / API |
|---|---|---|
| `/` (`index.html`) | public | Hero, live fee example (`GET /api/fee-rates`), latest products, top workshops |
| `/marketplace.html` | public | Product search: q, category, condition, city, price, sort, paging (`GET /api/products`) |
| `/product.html?id=` | public | Gallery, details, product passport, fee estimate, **Buy now** → `POST /api/orders` → checkout |
| `/repairers.html` | public | Search or "near me / near city" (`GET /api/repairers`, `/nearby`) with Leaflet + OSM map |
| `/repairer.html?id=` | public | Workshop profile, services, hours, reviews, map, **Request a repair** (`POST /api/repair-requests`) |
| `/checkout.html?orderId=` | customer | Exact order breakdown (`RL.fees.fromOrder`) and simulated payment (`POST /api/payments/simulate`) |
| `/login.html` | public | Sign in, `?next=` (same-origin paths only), demo-account quick fill |
| `/register-customer.html`, `/register-repairer.html` | public | Registration with per-field server errors; the repairer form has a map picker |
| `/customer/` | customer | Overview: stats, items needing attention |
| `/customer/buy.html`, `sell.html`, `listings.html` | customer | Browse; create a listing with a live "you receive" preview; edit and remove listings |
| `/customer/purchases.html`, `sales.html` | customer | Orders as buyer (pay, cancel, receipts, tracking) and as seller (net earnings, book delivery, refund) |
| `/customer/repairs.html`, `repair-request.html?id=` | customer | Repair list; request detail: quote breakdown, approve or reject, pay, progress, delivery legs |
| `/customer/payments.html`, `tracking.html`, `profile.html` | customer | Payment history and receipts; simulated deliveries with map; account details |
| `/repairer/` | repairer | Overview with the **profile-completion prompt**, stats, latest requests, low stock, earnings |
| `/repairer/requests.html`, `active.html`, `completed.html` | repairer | Job lists by status (`GET /api/repair-requests/incoming`) |
| `/repairer/repair-request.html?id=` | repairer | Accept or reject, inspect, quote with live fee preview, revise, progress, complete, use parts, book delivery |
| `/repairer/services.html`, `location.html`, `profile.html` | repairer | Sections of the workshop profile editor (`PUT /api/repairers/{id}`) |
| `/repairer/inventory.html` | repairer | Parts, stock in, count correction, discontinue or reactivate, movements, low-stock alert |
| `/repairer/earnings.html`, `payments.html` | repairer | Gross, commission and net earnings, payouts (simulated), transaction detail, refunds |

### Profile-completion prompt

A repairer's workshop is hidden from search until `GET /api/repairers/me` returns an empty `missingForListing`.
The overview turns those keys into a progress bar and a checklist:

- business details: `description`
- services: `categories`, `services`
- location: `location`, `pincode`, `repairMethods`
- hours: `workingHours`

Each checklist item links to the page that fixes it. `PUT /api/repairers/{id}` replaces the whole profile. The
editor therefore merges the section being edited with the current profile, and it also shows any other section
that is still missing, marked "Required to save". Otherwise the first save of a new profile would be rejected.

## Code layout

```
frontend/
  server.js                    dev server: static files, /api proxy, CSP and security headers, /healthz
  dev/mock-api.js              optional in-memory mock backend (dev only)
  scripts/check.js             static checks (npm test)
  public/
    *.html, customer/*.html, repairer/*.html
    assets/css/reloop.css      design system on top of Bootstrap (tokens, components, utilities)
    assets/img/favicon.svg
    assets/js/core/api.js      RL.api (fetch + JWT + ApiError), RL.session, RL.endpoints (every API path)
    assets/js/core/ui.js       RL.ui: escaping, money, dates, badges, toasts, loader/empty/error, tables,
                               pagination, forms + server fieldErrors, modal/confirm/prompt, receipts
    assets/js/core/layout.js   navbar, sidebar, footer, role guard (data-role), RL.page bootstrap
    assets/js/components/      fees, cards, tracking (Leaflet), location-picker, product-browser, payment-form,
                               refund, transactions, orders, repairer-profile
    assets/js/pages/           one script per page
```

Every page declares `<body data-layout="public|auth|dashboard" data-role="CUSTOMER|REPAIRER" data-nav="…">`.
`RL.page(fn)` runs the guard first. Without a session it redirects to `/login.html?next=…`; with the wrong role it
redirects to that role's home. Only then does it call `fn({account, role})`. The guard is a UX convenience only.
The services enforce every rule again: anonymous requests get 401, the wrong role gets 403, and another user's
resource gets 404.

### Money and fees

- All amounts come from the API. `*Paise` fields are integers; BigDecimal fields are rupees. `RL.ui.money(paise)` and
  `RL.ui.rupees(value)` format them as `₹1,020.00`.
- `RL.fees` renders one breakdown component everywhere:
  - customer pays = base + customer fee
  - provider receives = gross − commission
  - ReLoop keeps = fee + commission
- Exact breakdowns are built from what the server recorded (`fromOrder`, `fromQuote`, `fromTransaction`). Estimates
  (product page, listing preview, quote preview) use the live `GET /api/fee-rates`, are labelled "estimate", and
  round HALF_UP like the server. Rates are never hard-coded.
- If the recorded numbers do not add up, the component shows a warning instead of hiding it.

### Live refresh and cross-service status

Dashboards refresh themselves with `RL.poll(fetcher, apply, {interval})` (`core/ui.js`). Repair detail pages
refresh every 8 s, lists every 10 s, and overviews and earnings every 15 s. Polling runs only while the tab is
visible and redraws only when the data changed. It pauses while a dialog or form field is in use, backs off on
errors and shows "Live · updated …" under the page title. Payment state from order-service appears on both
sides:

* `RL.orders.statusHtml(order, role)` shows failed attempts ("nothing charged" / "no payout") and refunds;
* `RL.ui.paymentBadge(paymentStatus)` and the repair page's Payment card show the transaction id, a failed or
  cancelled attempt, and refunds;
* the repairer can refund from the repair page (Payment card → Refund). The refund dialog sends one
  `Idempotency-Key` per dialog.

See [INTEGRATION.md](INTEGRATION.md) §7.

### Simulated features

- **Payments:** only a method label and a demo outcome (success, declined, cancelled) are sent. No card, UPI or bank
  data is collected. The customer must tick a box confirming the exact total, and every attempt uses an
  `Idempotency-Key`. Declined and cancelled results are shown as such.
- **Deliveries:** always labelled "Simulated", with the API's own notice. Positions move only on "Demo: advance".

## Security notes

- **Content Security Policy:**
  - `script-src 'self'` plus jsDelivr and unpkg, with SRI on Bootstrap, Bootstrap Icons and Leaflet.
  - `connect-src 'self'`, so the API is only reachable through the proxy.
  - `object-src 'none'`, `base-uri 'self'`, `form-action 'self'`.
  - Inline `style=` attributes are allowed. Inline scripts are not.
- **Other headers:** `X-Content-Type-Options: nosniff`, `Referrer-Policy: same-origin` and `Permissions-Policy`
  (geolocation for this origin only; camera, microphone and payment off). Framing is not restricted by the dev
  server, so previews can embed it. Set `frame-ancestors` at the production proxy.
- **Proxy:** answers `/api/internal/**` with a 404 and strips any `X-Internal-Api-Key` header from the browser.
  Service-to-service calls never pass through it.
- **API data and links:** all API data is escaped with `RL.ui.esc` before it is inserted into HTML. URLs from the
  API go through `RL.ui.safeUrl`, which allows only `https:` and same-origin paths. Broken images fall back via
  `data-fallback`, not `onerror`.
- **Session:** the JWT is kept in `localStorage` (`reloop.session.v1`) together with its expiry. It is dropped on
  expiry and on any 401. `?next=` redirects accept same-origin paths only.
