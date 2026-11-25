# Repair booking workflow

A customer books a repair with a workshop. The repairer accepts the request, inspects the item and sends a quote. The customer approves or rejects the quote and pays (the payment is simulated). The repairer then does the work and marks it complete. Each change of status is saved in a timestamped history.

Two services take part. They talk **only through REST APIs** and never share a database:

| Concern | Owner | Why |
|---|---|---|
| Repair request, its status, history and notes | **repair-service** (8083) | the workflow lives here |
| Quotes: fee maths, frozen rates, revisions, approve/reject state | **order-service** (8082) | money is owned by one service |
| Payments, commission records, provider payouts | **order-service** | same |

repair-service keeps a read-only **copy** of the current quote (`QuoteSnapshot`, plus `latestQuoteId`) so it can show amounts without calling order-service on every read. The amounts always come from order-service.

## Statuses

```
REQUESTED ──accept──► ACCEPTED ──inspect──► INSPECTED ──quote──► QUOTED ◄────────────────┐
    │                    │                      │                 │  │  ▲ revise          │ revise
  reject               cancel                 cancel     approve  │  │  └─(QUOTED)        │
    ▼                    ▼                      ▼                 ▼  └─reject─► QUOTE_REJECTED
 REJECTED            CANCELLED              CANCELLED      QUOTE_APPROVED
                                                                  │ (system, immediately)
                                                                  ▼
                                                          AWAITING_PAYMENT ──revise──► QUOTED
                                                                  │ order-service confirms payment
                                                                  ▼
                                                                PAID ──progress──► IN_PROGRESS ──complete──► COMPLETED
                                                                                   (more progress notes)
```

All 12 values: `REQUESTED, ACCEPTED, REJECTED, INSPECTED, QUOTED, QUOTE_APPROVED, QUOTE_REJECTED, AWAITING_PAYMENT, PAID, IN_PROGRESS, COMPLETED, CANCELLED`. The terminal statuses are `REJECTED`, `COMPLETED` and `CANCELLED`.

The allowed moves are listed in one table, `RepairTransitions`. `RepairAction` maps each endpoint to the statuses where it is allowed and the reason it gives when it is refused. Every response includes `allowedActions` for the current viewer, so a frontend can show only the buttons that work.

## Rules and where they are enforced

| Rule | Enforcement |
|---|---|
| The customer must approve the quote | Only `QUOTE_APPROVED` leads to `AWAITING_PAYMENT`. order-service accepts a payment only for an `APPROVED` quote |
| Rejected quotes never reach payment | repair-service: `QUOTE_REJECTED` can't move to `PAID` (409). order-service: paying a `REJECTED` quote gives 409 *"rejected quotes can't be paid"* |
| Revised quotes need fresh approval | A revision (allowed from `QUOTED`, `QUOTE_REJECTED` or `AWAITING_PAYMENT`) moves the request back to `QUOTED`. order-service marks the old quote `SUPERSEDED`, so an old approval can't be paid. repair-service also rejects a payment confirmation for any quote that isn't the current one |
| Only paid jobs can enter `IN_PROGRESS` | `IN_PROGRESS` can be reached only from `PAID` (or from `IN_PROGRESS` itself, for progress notes). Anything else gives 409 with `currentStatus` |
| Only the assigned repairer can update a request | Every repairer action checks that the request's workshop belongs to the caller's account. Any other repairer gets **404**, so request ids don't leak |
| Customers see only their own requests | Every customer read or action checks `customerAccountId`. Others get **404**. `/mine` returns only the caller's requests |
| Payment must match the quote | The confirmation must name the current quote, the request must be `AWAITING_PAYMENT`, and the amount must equal `totalPayablePaise` exactly. A repeat with the same `paymentTransactionId` is a no-op |
| Notes where they matter | Reject (reason), cancel (reason), inspect (findings), and every progress update after the first need a note. Without one: 400 |
| Cancellation only before payment | Customer: `REQUESTED` to `AWAITING_PAYMENT`. Repairer: `ACCEPTED` to `AWAITING_PAYMENT` (a new request is rejected, not cancelled) |
| Concurrent edits | `@Version` on `RepairRequest`. A clash gives 409 *"changed by someone else"* |

## Money

- The repairer enters the price in rupees (`"amount": 1500.00`, up to 2 decimals). repair-service converts it to **paise** and sends only that number.
- order-service applies `reloop.fees.customer-fee-rate` (default `0.0200`) and `reloop.fees.repairer-commission-rate` (default `0.0200`). Each fee is `round(base × rate)` to the nearest paisa (HALF_UP). The rates come from configuration and are not hard-coded.
- Rates are **frozen** when a quote is issued. The payment copies the quote's frozen amounts and rates, so a later configuration change never alters a quote the customer has already seen.
- Example, ₹1,500.00 repair: customer fee ₹30.00, so the customer pays **₹1,530.00**. Commission is ₹30.00, so the repairer gets **₹1,470.00** and the platform keeps ₹60.00.
- On success, order-service writes three **separate** rows: `payment_transactions` (SUCCESS), `commission_records` (fee + commission) and `provider_payouts` (net, PENDING).
- Payments are **simulated**. There are no fields for card numbers, CVV, UPI PIN or bank details, and none must ever be added.

## API — repair-service (`/api/repair-requests`, Bearer JWT from product-service)

| Method & path | Who | Body | Result |
|---|---|---|---|
| `POST /api/repair-requests` | CUSTOMER | `CreateRepairRequest` | 201 + `Location`, `REQUESTED` |
| `GET /api/repair-requests/mine?status=&page=&size=` | CUSTOMER | – | own history, newest first |
| `POST /{id}/quote/approve` | CUSTOMER (owner) | `{"note"?}` | `AWAITING_PAYMENT` |
| `POST /{id}/quote/reject` | CUSTOMER (owner) | `{"note"?}` | `QUOTE_REJECTED` |
| `GET /api/repair-requests/incoming?status=` | REPAIRER | – | requests to the caller's workshop |
| `POST /{id}/accept` | REPAIRER (assigned) | `{"note"?}` | `ACCEPTED` |
| `POST /{id}/reject` | REPAIRER (assigned) | `{"note": reason}` | `REJECTED` |
| `POST /{id}/inspect` | REPAIRER (assigned) | `{"findings"}` | `INSPECTED` |
| `POST /{id}/quote` | REPAIRER (assigned) | `{"amount","summary","estimatedDays"?}` | `QUOTED` (first quote, after inspection) |
| `POST /{id}/quote/revise` | REPAIRER (assigned) | same | `QUOTED`, revision n+1 |
| `POST /{id}/progress` | REPAIRER (assigned) | `{"note"}` (optional the first time) | `IN_PROGRESS` |
| `POST /{id}/complete` | REPAIRER (assigned) | `{"note"?}` | `COMPLETED` |
| `PATCH /{id}/status` | REPAIRER (assigned) | `{"status","note"}` | generic form of accept, reject, inspect, progress, complete and cancel |
| `GET /{id}` | owner or assigned repairer | – | full details, quote, `allowedActions`, timeline |
| `GET /{id}/history` | owner or assigned repairer | – | `[{fromStatus,toStatus,actor,note,at}]` |
| `POST /{id}/cancel` | owner or assigned repairer | `{"note": reason}` | `CANCELLED` |
| `GET /api/repair-requests/demo-images` | public | – | bundled placeholder image paths |

**`CreateRepairRequest`**

```json
{
  "repairerProfileId": 3,
  "repairServiceId": 7,
  "category": "MOBILE",
  "itemDescription": "Samsung Galaxy S21",
  "problemDescription": "Screen cracked after a fall; touch stops working in the top half.",
  "imageUrls": ["/demo-images/repairs/phone-cracked-screen.svg"],
  "deliveryMethod": "SEND_ITEM",
  "customerNotes": "Evenings preferred"
}
```

- `repairServiceId` is optional.
- `deliveryMethod` must be one the workshop offers: `IN_PERSON`, `SEND_ITEM` or `PICKUP_DROPOFF`.
- `category` must be one the workshop repairs.
- Up to 5 `imageUrls`. Each must be a bundled `/demo-images/...` path or an `https://` URL. There is no real image hosting yet.
- Bundled placeholders: `phone-cracked-screen`, `laptop-no-power`, `appliance-fault`, `bicycle-damage` and `generic-item` (all `.svg`), under `repair-service/src/main/resources/static/demo-images/repairs/`.

**Errors** use the shared JSON shape `{timestamp,status,error,message,path}`:

| Status | Meaning |
|---|---|
| 400 | Adds `fieldErrors` |
| 401 | No token or wrong internal key |
| 403 | Wrong role |
| 404 | Not found, or not yours |
| 409 | Wrong state; adds `currentStatus` |
| 502 / 503 | order-service gave an error or is down. The request is left unchanged |

## API — order-service

| Method & path | Who | Notes |
|---|---|---|
| `POST /api/internal/repair-quotes` | repair-service (`X-Internal-Api-Key`) | Issues revision 1, or revision n+1 that supersedes the open one. Refused (409) once the quote is paid |
| `POST /api/internal/repair-quotes/{id}/approve` | repair-service | `SENT` → `APPROVED`. Idempotent. 409 if superseded, rejected or expired (`reloop.quotes.valid-days`, default 7) |
| `POST /api/internal/repair-quotes/{id}/reject` | repair-service | `SENT` → `REJECTED`. Idempotent |
| `GET /api/internal/repair-quotes/{id}` | repair-service | read |
| `POST /api/payments/simulate` | CUSTOMER (quote owner) | `{"repairQuoteId", "method"?: UPI\|CARD\|NET_BANKING\|WALLET, "outcome"?: SUCCESS\|FAILURE}` |
| `GET /api/payments/{transactionId}` | CUSTOMER (payer) | payment + commission + payout references |

### Payment sequence

```
customer ──POST /api/payments/simulate──► order-service
                                           │ quote is the caller's, APPROVED, not yet paid?
                                           │ write PaymentTransaction(SUCCESS) + CommissionRecord + ProviderPayout
                                           │──POST /api/internal/repair-requests/{id}/payment-confirmation──► repair-service
                                           │      {quoteId, paymentTransactionId, transactionId, amountPaise}
                                           │◄───────────────── 200 (request now PAID)  or 409 / error
                                           │ commit, or ROLL BACK everything if repair-service refused or was unreachable
customer ◄──201 PaymentView────────────────┘
```

Because the callback runs inside order-service's database transaction, a payment that repair-service refuses (for example, because the request was cancelled meanwhile) or can't receive leaves **no** payment, commission or payout rows behind. A simulated `FAILURE` only records a FAILURE transaction, and the quote stays payable.

Service-to-service calls use the shared `reloop.internal.api-key`, compared in constant time. Base URLs are `reloop.services.order.base-url` and `reloop.services.repair.base-url` (or the `RELOOP_ORDER_SERVICE_URL` environment variable). Timeouts are 2 s to connect and 5 s to read.

## Try it (all three services running)

```bash
# 1. log in at product-service (demo accounts: docs/AUTH.md)
CUST=$(curl -s localhost:8081/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"customer.demo@reloop.local","password":"ReLoopDemo#Cust2026"}' | jq -r .accessToken)
REP=$(curl -s localhost:8081/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"repairer.demo@reloop.local","password":"ReLoopDemo#Rep2026"}' | jq -r .accessToken)
PROFILE=$(curl -s localhost:8083/api/repairers/me -H "Authorization: Bearer $REP" | jq .id)
# (the workshop must be complete and listed; if not, fill it in first, as described in docs/REPAIRERS.md)

# 2. customer books
ID=$(curl -s localhost:8083/api/repair-requests -H "Authorization: Bearer $CUST" -H 'Content-Type: application/json' \
  -d "{\"repairerProfileId\":$PROFILE,\"category\":\"MOBILE\",\"itemDescription\":\"Galaxy S21\",
       \"problemDescription\":\"Screen cracked after a fall, touch dead at the top\",
       \"imageUrls\":[\"/demo-images/repairs/phone-cracked-screen.svg\"],\"deliveryMethod\":\"IN_PERSON\"}" | jq .id)

# 3. repairer accepts, inspects, quotes
R="Authorization: Bearer $REP"; J='Content-Type: application/json'
curl -s -X POST localhost:8083/api/repair-requests/$ID/accept -H "$R"
curl -s -X POST localhost:8083/api/repair-requests/$ID/inspect -H "$R" -H "$J" -d '{"findings":"OLED panel cracked, frame fine"}'
QUOTE=$(curl -s -X POST localhost:8083/api/repair-requests/$ID/quote -H "$R" -H "$J" \
  -d '{"amount":1500,"summary":"Replace OLED panel","estimatedDays":2}' | jq .quote.quoteId)

# 4. customer approves and pays (simulated)
curl -s -X POST localhost:8083/api/repair-requests/$ID/quote/approve -H "Authorization: Bearer $CUST"
curl -s localhost:8082/api/payments/simulate -H "Authorization: Bearer $CUST" -H "$J" -d "{\"repairQuoteId\":$QUOTE}"

# 5. repairer works and completes; anyone involved can read the timeline
curl -s -X POST localhost:8083/api/repair-requests/$ID/progress -H "$R"
curl -s -X POST localhost:8083/api/repair-requests/$ID/complete -H "$R" -H "$J" -d '{"note":"Ready for pickup"}'
curl -s localhost:8083/api/repair-requests/$ID/history -H "Authorization: Bearer $CUST" | jq
```

## Tests

| Service | Test | Covers |
|---|---|---|
| repair-service | `repairrequest/RepairRequestFlowTest` | full journey and its timeline. Rejected quote can't be paid; revised quote needs fresh approval; no `IN_PROGRESS` before `PAID`; amount mismatch and duplicate confirmations; required notes; cancellation window; order-service down or refusing; validation. order-service is a `@MockitoBean` |
| repair-service | `repairrequest/RepairRequestAccessTest` | owner and assigned repairer only (others get 404), wrong role 403, anonymous 401, internal key 401 |
| repair-service | `service/RepairTransitionsTest` | state-machine invariants, `allowedActions` per viewer |
| repair-service | `client/HttpOrderServiceClientTest` | REST contract to order-service (MockRestServiceServer), error mapping 409/502/503 |
| order-service | `repairquote/RepairQuoteApiTest` | key check, fee freeze, revisions supersede, approve/reject rules, expiry |
| order-service | `repairquote/PaymentApiTest` | pays only an APPROVED quote, once, as its owner; frozen rates; separate commission and payout; rollback when repair-service refuses or is down; simulated failure |
| order-service | `service/FeeCalculatorTest`, `client/HttpRepairServiceClientTest` | fee arithmetic and rounding; REST contract to repair-service |

## Not yet

- Customer reviews of completed repairs.
- Spare-part usage, delivery tracking and refunds. The tables exist but these flows don't yet.
- An outbox/retry for the payment callback. Today a failed callback rolls the payment back, and the customer retries.
