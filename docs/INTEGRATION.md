# ReLoop service integration

How the three services and the frontend work together for the four core flows: marketplace sale, repair,
failed payment and refund. The guide covers the REST contracts between services, how IDs line up, how duplicate
updates are prevented, how errors are handled and how both dashboards stay up to date. It ends with run
instructions and the end-to-end scenarios.

```
                         browser (customer / repairer dashboards, polling every 8–15 s)
                                              │  same origin, Bearer JWT
                                              ▼
                              frontend/server.js :8080  (static files + /api proxy)
                ┌─────────────────────────────┼─────────────────────────────┐
                ▼                             ▼                             ▼
     product-service :8081          order-service :8082           repair-service :8083
     accounts, JWT, products,       orders, payments, fees,       repairer profiles, repair
     passport, stores               commission, payouts, refunds  requests, quotes UI, inventory
     H2 ./data/reloop_product       H2 ./data/reloop_order        H2 ./data/reloop_repair
                │  ▲                     │   │   ▲                        │
                │  └── reserve / confirm-sale / release / sale-refund ───┘│   │
                │                         │   └── payment-confirmation / payment-events ──▶│
                └── create repairer profile ──────────────────────────────────────────────▶│
                                          ▲── create quote / approve quote ─────────────────┘
```

* **REST only, no shared database.** Each service has its own H2 file (`<service>/data/`). No service reads
  another service's tables. Internal calls go to `/api/internal/**` (product-service also uses
  `/api/repairer-profiles`) with the header `X-Internal-Api-Key` (`reloop.internal.api-key`, env
  `RELOOP_INTERNAL_API_KEY`). The dev proxy never forwards `/api/internal/**` from the browser (it returns 404).
* **One identity.** product-service issues the JWT. All services verify it with the shared `reloop.jwt.secret`,
  so `account.id` is the same number everywhere.

## 1. Mismatch audit (done before the frontend work)

Every internal call was checked for the URL, the HTTP method, each JSON field (name, type and nullability), the
status codes and the auth header, on both sides. The DTO pairs that already existed all matched. These gaps
were found and fixed in the services:

| # | Gap | Fix |
|---|-----|-----|
| G1 | A **refund** never reached product-service or repair-service. The product passport and the repair request kept showing a sale or a paid job that had been reversed. A **failed / cancelled** repair payment was never reported, so the repairer could not see it. | New endpoints `POST /api/internal/products/{id}/sale-refund` (product-service) and `POST /api/internal/repair-requests/{id}/payment-events` (repair-service). order-service calls them (§3). |
| G2 | A **repeated payment confirmation** (order-service retry, or a new attempt after a rollback) for the same quote returned 409 once the request was PAID. The retry then failed even though nothing was wrong. | Same quote with the same ids → no-op. Same quote with different ids (an earlier attempt was rolled back) → the new reference is adopted. Wrong amount → 409. |
| G3 | The repair request showed only an internal payment number, not the **`transactionId`** (`TXN-…`) that the customer sees on receipts and the repairer sees on payouts. | `transactionId` is on `RepairRequestResponse` and on the list summary. |
| G4 | Neither dashboard could tell a "never paid" request apart from a "payment failed" one. | `paymentStatus` (`PAID`, `FAILED`, `CANCELLED`, `PARTIALLY_REFUNDED`, `REFUNDED`), `lastPaymentFailure` and `refundedAmountPaise` are on repair requests. Orders already carry `lastPaymentFailure`. Provider payout lists deliberately contain only successful payments, so a failed payment never creates a payout row. |
| G5 | A **refund could be applied twice** on a double click or a client retry. | The refund endpoints accept `Idempotency-Key` (≤ 100 chars, unique per payment). A repeat returns the same result with `Idempotent-Replay: true`. The frontend refund dialog sends one key per dialog. |
| G6 | Contracts were only checked by unit tests on each side, so the two sides could drift apart. | Shared example payloads in [`contracts/`](../contracts) are asserted by a `ServiceContractTest` in **every** service (see §2). |
| G7 | Found by the real-stack e2e job: on a **fresh database** product-service crashed at startup. The demo seeder still used the legacy one-step purchase, which production disables because sales go through order-service. The test config turned it back on, so unit tests never noticed. | The seeder sells the demo item through the same reserve → confirm-sale path as order-service. The seeder test now runs with the production setting. |

## 2. Contract table

Each row below has an example payload in `contracts/`. Three `ServiceContractTest` classes check those payloads:

* `order-service/src/test/java/com/reloop/orderpayment/contract/ServiceContractTest.java`
* `product-service/src/test/java/com/reloop/product/contract/ServiceContractTest.java`
* `repair-service/src/test/java/com/reloop/repair/contract/ServiceContractTest.java`

Each test checks two things. The **caller** must serialise exactly the example payload: its HTTP client is run
against `MockRestServiceServer`. The **receiver** must accept it with strict Jackson and Bean Validation, and
its response must contain every example field with the same JSON type. If either side changes a field, the
build fails.

| Caller → receiver | Endpoint | Request / response example | Idempotent by |
|---|---|---|---|
| order → product | `POST /api/internal/products/{productId}/reserve` | `order-to-product/reserve.request.json` → `product-sale.response.json` | `orderReference` |
| order → product | `POST …/{productId}/confirm-sale` | `confirm-sale.request.json` → `product-sale.response.json` | `orderReference` |
| order → product | `POST …/{productId}/release` | `release.request.json` | `orderReference` |
| order → product | `POST …/{productId}/sale-refund` | `sale-refund.request.json` → `product-sale.response.json` | `refundReference` |
| order → repair | `POST /api/internal/repair-requests/{id}/payment-confirmation` | `order-to-repair/payment-confirmation.request.json` | quote + ids (G2) |
| order → repair | `POST /api/internal/repair-requests/{id}/payment-events` | `payment-event.failed.request.json`, `payment-event.refunded.request.json` | `eventId` (unique column) |
| repair → order | `POST /api/internal/repair-quotes` | `repair-to-order/create-quote.request.json` → `repair-quote.response.json` | one open quote per request |
| repair → order | `POST /api/internal/repair-quotes/{quoteId}/approve` | `quote-decision.request.json` → `repair-quote.response.json` | quote status |
| repair → order | `GET /api/internal/orders/{id}` | `order.response.json` | read only |
| product → repair | `POST /api/repairer-profiles` | `product-to-repair/create-profile.request.json` → `repairer-profile.response.json` | `accountExternalId` |

## 3. Flows, step by step

### Marketplace sale
1. The seller creates a listing with `POST /api/products` (product-service).
2. The buyer calls `POST /api/orders {productId}` (order-service). order-service calls **reserve** with the
   order number as `orderReference`, and the product becomes `RESERVED`.
3. The buyer calls `POST /api/payments/simulate {orderId, outcome:"SUCCESS", amountPaise}` with an
   `Idempotency-Key`. In one transaction, order-service records the payment, the customer fee, the commission
   (`COM-…`), the payout (`PO-…`) and the ledger entries. As the **last** step it calls **confirm-sale**, and
   the product becomes `SOLD` with a passport `SOLD` event. If confirm-sale fails, the whole payment rolls back
   and the client gets 409, 502 or 503, so nothing is half-done.
4. The seller's *My sales* page shows the order `CONFIRMED` with gross, commission and net. The buyer's
   *My purchases* page shows `CONFIRMED` with a receipt.

### Repair
1. The customer creates a request. The repairer accepts it, inspects the item and quotes (repair-service).
2. The quote is priced by order-service with `POST /api/internal/repair-quotes`, so the fees are the same as
   on every receipt.
3. The customer approves with `/quote/approve`, and repair-service calls `…/approve` on order-service.
4. The customer pays with `POST /api/payments/simulate {repairQuoteId, …}`. order-service records the payment,
   commission and payout, then calls **payment-confirmation**. repair-service sets the status to `PAID`,
   `paymentStatus` to `PAID`, and stores the `transactionId`. A failure rolls the payment back.
5. The repairer sees the job as paid under *Ready to repair*, uses parts from inventory
   (`POST /api/inventory/parts/{id}/use` with the `repairRequestId`), posts progress and completes the job.
   Both detail pages poll and show the new status.

### Failed or cancelled payment
* order-service stores the `FAILED` / `CANCELLED` payment row. No commission, payout or ledger entry is
  created, and nothing is sent to product-service.
* Order: it stays `PENDING_PAYMENT`, `lastPaymentFailure` is set, and the product stays `RESERVED`. The buyer
  sees "Payment failed · nothing charged" and the seller sees "Buyer's payment failed · not confirmed, no
  payout".
* Repair: order-service sends a **payment-event** with type `FAILED` or `CANCELLED`. This is best-effort: the
  failed row is kept even if repair-service is down. repair-service records the event only while the request
  is `AWAITING_PAYMENT`. The status does **not** change, and `paymentStatus` and `lastPaymentFailure` are set.
  Both sides see a red alert, and the customer can pay again with a new key.

### Refund
1. The provider (seller or repairer) refunds with `POST /api/transactions/{txn}/refunds {baseAmountPaise?,
   reason, note}` and an `Idempotency-Key`. Support can use `/api/internal/transactions/{txn}/refunds`. The
   buyer gets 403.
2. order-service splits the refund (base plus the matching customer fee), reverses the commission and the
   payout in proportion, and writes the reversing ledger entries. The payment and the order become
   `PARTIALLY_REFUNDED` or `REFUNDED`.
3. The refund is then propagated **synchronously, as the last step**. If that call fails, the refund rolls
   back (409, 502 or 503) and can simply be retried with the same key:
   * Marketplace: **sale-refund** goes to product-service. The product stays `SOLD`, and one `SALE_REFUNDED`
     passport event is added per `refundReference`.
   * Repair: a **payment-event** with type `REFUNDED` goes to repair-service. It sets `paymentStatus` and
     `refundedAmountPaise`. A full refund of a `PAID` or `IN_PROGRESS` job closes it as `CANCELLED` (actor
     SYSTEM). A `COMPLETED` job stays completed, with a history note.
4. The customer's *Payments* page shows the refund, the seller's or repairer's payout shows "… reversed by
   refund", and the earnings totals drop.

## 4. ID map: one ID per thing, the same everywhere

| ID | Owner | Format / example | Carried to |
|---|---|---|---|
| Account id | product-service | `1`, `4` (JWT `sub`) | every service as `buyerAccountId`, `sellerAccountId`, `customerAccountId`, `repairerAccountId`, `accountExternalId` |
| Product id | product-service | `42` | order `productId`, every product internal call |
| Order number | order-service | `ORD-2026-1006` | product-service `orderReference` / `reservationReference` |
| Transaction id | order-service | `TXN-20261125-ABCDEFGHJK` | receipts, payouts, repair `transactionId`, product sale / refund, eventIds |
| Payment row id | order-service | `34` | repair `paymentTransactionId` (internal only) |
| Refund reference | order-service | `RFD-20261125-MNPQRSTUVW` | product `refundReference` |
| Repair request id / number | repair-service | `7` / `RR-2026-1006` | order-service quote `repairRequestId`, `requestNumber` |
| Quote id | order-service | `12` | repair-service quote, `repairQuoteId` in payments |
| Repairer profile id | repair-service | `1` | order-service quote `repairerProfileId` |
| Payment event id | order-service | `TXN-…:FAILED`, `TXN-…:CANCELLED`, `TXN-…:REFUND:<totalRefundedPaise>` | repair-service `repair_payment_events.event_id` (unique) |

Money is always `long` paise in internal contracts. Public quote views show rupees.

## 5. Duplicate updates: how they are prevented

| Operation | Key | Behaviour on repeat |
|---|---|---|
| Simulated payment | `(buyer, Idempotency-Key)` | Same transaction returned (HTTP 200, `Idempotent-Replay: true`). Same key for a different target → 409. |
| Paying a settled order or quote | order / quote status | 409, so a second payment never happens |
| Refund | `(payment, Idempotency-Key)` (unique `uk_refund_idempotency`) | Same result, `Idempotent-Replay: true`. A refund on a fully refunded payment → 409. |
| Reserve / confirm-sale | `orderReference` | Same answer. Another order → 409. |
| Sale-refund | `refundReference` | One passport event per reference |
| Payment confirmation | quote + ids | No-op, or adopts the new reference after a rollback (G2) |
| Payment events | `eventId` (unique) | Ignored when already recorded. The refund eventId includes the running total, so each partial refund counts once. |
| Frontend | one key per payment form / refund dialog | Double clicks and retries reuse the key, and a new key is made only after a failed attempt |

## 6. Error handling

* **Timeouts:** every internal client uses a 2 s connect timeout and a 5 s read timeout.
* **Status mapping in clients:** 404, 403 and 409 from the other service are passed through with its message.
  If the service cannot be reached the client answers **503**. Any other failure becomes **502**. All
  responses use the common error body `{status, error, message, path, fieldErrors}`, plus context such as
  `orderStatus` or `paymentStatus`.
* **Order of operations:** the cross-service call is the **last** step inside the local transaction. A remote
  failure rolls back the local change, so the services never disagree. The one exception is best-effort
  reporting of failed payments: losing that report only hides a hint and never moves money.
* **Frontend:** every API error appears as a toast or an inline error with a retry button. The polling helper
  backs off on errors and shows *"Connection problem · retrying in N s"*.

## 7. Dashboard synchronisation (MVP polling)

`RL.poll(fetcher, apply, {interval})` in `frontend/public/assets/js/core/ui.js`:

* refreshes every **8 s** on repair detail pages, **10 s** on lists and **15 s** on overviews and earnings, and
  only while the tab is visible (it refreshes immediately when the tab becomes visible again);
* compares a JSON fingerprint and redraws only when the data changed, without loader flicker;
* pauses while a dialog is open or a form field has focus, so typing is never lost;
* backs off ×2, ×4 … up to 60 s on errors and stops on 401 or 403;
* shows *"Live · updated hh:mm:ss"* under the page title.

Wired into: customer overview, purchases, sales, repairs, payments and the repair detail page; repairer
overview, job lists (requests, active, completed), payments and earnings.

Status shown on both sides:

| Situation | Customer sees | Repairer / seller sees |
|---|---|---|
| Sale paid | Order `Confirmed`, receipt | Order `Confirmed`, gross − commission = net, payout `Pending` |
| Payment failed (order) | `Pending payment` + "Payment failed · nothing charged" | `Pending payment` + "Buyer's payment failed · not confirmed, no payout" |
| Payment failed (repair) | `Awaiting payment` + "Last payment failed" alert, pay again | Same badge and alert: "not confirmed … no payout was created" |
| Repair paid | `Paid` + Payment card (transaction id) | `Paid` + "Payment confirmed … payout pending", job under *Ready to repair* |
| Refunded | `Refunded` badge, amount refunded, payment history | Payout "₹… reversed by refund", net reduced, repair `Cancelled` + `Refunded` |

## 8. Running it

```bash
# real stack (Java 21, Maven, Node 18+): builds the jars if needed and waits until everything is ready
scripts/e2e/start-stack.sh            # --build to rebuild, --fresh to start with clean databases
open http://localhost:8080            # demo logins in docs/AUTH.md
scripts/e2e/start-stack.sh --stop

# or by hand, each in its own terminal and from its module directory (own ./data DB):
(cd repair-service && mvn spring-boot:run)
(cd order-service && mvn spring-boot:run)
(cd product-service && mvn spring-boot:run)
(cd frontend && node server.js)       # PRODUCT_API / ORDER_API / REPAIR_API override the service URLs

# no Java? the same UI and scenarios run against the in-memory mock
(cd frontend && node server.js --mock)
```

**Existing local databases:** this integration added columns (`paymentStatus`, `transactionId`,
`refundedAmountPaise` and `lastPaymentFailure` on repair requests; `idempotencyKey` on refunds), the
`repair_payment_events` table and the unique constraint `uk_refund_idempotency`. `ddl-auto=update` adds the
columns and tables, but it does not always add constraints to existing tables. Use `start-stack.sh --fresh`,
or delete `product-service/data`, `order-service/data` and `repair-service/data`, to start clean. The demo
accounts are seeded again.

All services must share `reloop.jwt.secret` and `RELOOP_INTERNAL_API_KEY`. The dev defaults already match.

## 9. End-to-end scenarios

`scripts/e2e/` uses bash, curl and jq. Every scenario goes through the frontend proxy on `BASE_URL` (default
`http://localhost:8080`), just like the browser. Each run creates its own products, orders and repairs, so the
scenarios can run again on the same data.

| Script | Checks |
|---|---|
| `01-marketplace-sale.sh` | Fee maths (₹1000 → total ₹1020, commission ₹20, net ₹980); order `CONFIRMED` for buyer and seller; product `SOLD` in product-service; exactly one payout; payment appears once in buyer history; receipt; replayed payment returns the same transaction |
| `02-repair-flow.sh` | Request → accept → inspect → quote (₹2400 → ₹2448) → approve → pay; repair `PAID`, `paymentStatus` `PAID` and the same `transactionId` in repair-service for both sides; repairer payout gross ₹2400 / commission ₹48 / net ₹2352; part used and stock decreased; progress and completion visible to the customer |
| `03-failed-payment.sh` | Declined order payment: order stays `PENDING_PAYMENT` with the failure visible to buyer and seller, product not `SOLD`, no payout. Declined repair payment: `AWAITING_PAYMENT` + `paymentStatus` `FAILED` for both sides, no payout. The retry then succeeds. |
| `04-refund.sh` | Buyer can't refund (403); full refund → `REFUNDED`, customer gets base + fee; same key replays (`Idempotent-Replay: true`, still one refund); new refund → 409; order `REFUNDED`; payout net 0 and commission reversed; buyer history; passport `SALE_REFUNDED`. Repair refund → repair `CANCELLED` + `REFUNDED` for both sides, payout reversed. |

```bash
scripts/e2e/run-all.sh                          # all four, exit code = failed scenarios
BASE_URL=http://localhost:8080 scripts/e2e/03-failed-payment.sh
E2E_VERBOSE=1 scripts/e2e/04-refund.sh          # print every request and response
```

CI (`.github/workflows/ci.yml`) runs them twice: against the mock in the *Frontend checks* job, and against
the **real** services in the *End-to-end (real services)* job. That job builds the three jars, starts them
with `start-stack.sh`, runs `run-all.sh`, and prints the service logs if it fails.

### Manual walkthrough (two browsers, or one normal and one private window)
1. **Sale:** Karan buys one of Asha's listings and pays. Within about 10 s, Asha's *My sales* page shows it
   `Confirmed` with the net amount, without a reload.
2. **Repair:** Asha requests a repair from *Mehta Fix-It Workshop*. Ravi accepts, inspects and quotes, and
   Asha's request page shows the quote within about 8 s. Asha approves and pays, and Ravi's page turns `Paid`
   with a toast. Ravi uses a part and completes the job.
3. **Failed payment:** on a new quote, Asha picks the demo outcome "Payment is declined". Both pages show
   "Last payment failed". Ravi's job is not under *Ready to repair*, and his payouts page has no new row.
4. **Refund:** Ravi opens the Payment card → *Refund*. Asha sees `Cancelled` + `Refunded`. Her *Payments*
   page shows the refund, and Ravi's payouts page shows "reversed by refund".
