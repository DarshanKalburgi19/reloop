# Orders, commission and payments (simulated)

order-service (port **8082**, `com.reloop.orderpayment`) takes marketplace orders, simulates payments for orders and
repair bookings, applies ReLoop's fees, and keeps the money records: payments, commission, provider payouts, refunds
and a double-entry ledger. It also produces receipts and the numbers shown on customer, seller and repairer
dashboards.

> **Everything here is a simulation.** No money moves and no payment gateway is contacted. The API never accepts
> card, UPI or bank details and never stores them; if a client sends such fields anyway, they are ignored (a test
> checks this). `POST /api/payments/simulate` is a demo endpoint: the caller picks the outcome.

---

## 1. Money rules

| Rule | How it is enforced |
|---|---|
| Amounts in **integer paise** (`long`), rates as `BigDecimal` (scale 6) | All entities and DTOs; `FeeCalculator` uses `BigDecimal` with `HALF_UP` rounding to whole paise |
| Fees are **configurable**, never hard-coded | `reloop.fees.customer-fee-rate` and `reloop.fees.provider-commission-rate` (env `RELOOP_CUSTOMER_FEE_RATE`, `RELOOP_PROVIDER_COMMISSION_RATE`, both default `0.0200`). A per-transaction-type database override (`fee_rate_configs`) wins when present (see §5) |
| The **exact rate applied is stored** | An order or quote freezes its rates when it is created. Its payment copies them, and so do its commission record and payout. A later change to the rates never alters existing orders, quotes or payments |
| Customer fee and provider fee are **separate** | `customerFeePaise` is added to what the customer pays. `providerCommissionPaise` is deducted from the provider's gross amount. Both are stored separately on the order or quote, the payment and the commission record |
| ReLoop revenue = customer fee + provider commission | `platformRevenuePaise` on the order, payment and commission record |
| Commission and payout are separate records | A `CommissionRecord` (ReLoop's side) and a `ProviderPayout` (the seller's or repairer's side) per successful payment. Earnings are read from payouts and never worked out by summing payments |

### The example (2% + 2%)

| | Paise | ₹ |
|---|---:|---:|
| Base price (item or repair) | 100000 | 1,000.00 |
| Customer fee 2% | 2000 | 20.00 |
| **Customer pays** | **102000** | **1,020.00** |
| Provider gross | 100000 | 1,000.00 |
| Provider commission 2% | 2000 | 20.00 |
| **Provider net** | **98000** | **980.00** |
| **ReLoop revenue** | **4000** | **40.00** |

Rounding: each fee is `round_half_up(base × rate)`, worked out separately. So ₹0.25 (25 paise) at 2% gives a 1 paisa
fee: 25 × 0.02 = 0.5, which rounds up. Invariant: `customer pays = provider net + ReLoop revenue`
(`CommissionCalculationTest`).

---

## 2. Transaction types

| Type | What the customer pays for | Provider | Created by |
|---|---|---|---|
| `MARKETPLACE_PURCHASE` | A second-hand listing | Seller (`providerType = SELLER`, the seller's account id) | `POST /api/orders`, then `POST /api/payments/simulate {"orderId"}` |
| `REPAIR_BOOKING` | An approved repair quote | Repairer (`providerType = REPAIRER`, the repairer's **account** id, plus `providerId` = repairer profile id) | repair-service quote, then customer approval, then `POST /api/payments/simulate {"repairQuoteId"}` |

### Marketplace purchase sequence

```
buyer ──POST /api/orders {productId}──► order-service ──POST /api/internal/products/{id}/reserve──► product-service
                                          │                     (ACTIVE → RESERVED for this order, history RESERVED)
                                          │◄───────────── price, seller, title
                                          │ order PENDING_PAYMENT with frozen fees; pay within 30 minutes
buyer ◄─201 order (totalPaise = 102000)───┘

buyer ──POST /api/payments/simulate {orderId, method, outcome, amountPaise}──► order-service
                         │ lock order · owner? payable? not expired? amount == totalPaise?
       FAILED/CANCELLED  │ record the attempt only (no commission, payout or ledger); the order stays payable
       SUCCESS           │ PaymentTransaction + CommissionRecord + ProviderPayout(PENDING) + 4 ledger lines
                         │ order CONFIRMED
                         │──POST /api/internal/products/{id}/confirm-sale──► product-service (RESERVED → SOLD,
                         │                                                    passport owner = buyer, history SOLD)
                         │ commit, or ROLL BACK everything if product-service refused or was unreachable
buyer ◄─201 transaction──┘
```

An unpaid order is cancelled when its payment window (`reloop.orders.payment-window-minutes`, default 30) runs out.
This happens on a sweep every `reloop.orders.expiry-sweep.interval-ms` (default 60 s), or when the buyer calls
`POST /api/orders/{id}/cancel`. The reservation is then released (`/release`, history `RESERVATION_RELEASED`). A release that fails is retried on the next sweep.

> **Direct purchase is off.** product-service's old `POST /api/products/{id}/purchase` skipped all fees and payments
> (audit issue C-1). It now returns **410 Gone** unless `RELOOP_DIRECT_PURCHASE_ENABLED=true`. Product-service's
> own tests enable it to keep covering the legacy path.

### Repair booking sequence

The quote and approval endpoints live in **repair-service (8083)**, because it owns the repair request and its
status. order-service calculates and freezes the fees.

```
repairer ──POST /api/repairs/{id}/quote {amount, summary, estimatedDays}──► repair-service
                         │──POST /api/internal/repair-quotes──► order-service (fees frozen, quote SENT)
                         │ request QUOTED (a revision if already quoted; the old quote is SUPERSEDED)
customer ──POST /api/repairs/{id}/approve──► repair-service ──► order-service quote APPROVED · request AWAITING_PAYMENT
customer ──POST /api/payments/simulate {repairQuoteId, method, outcome, amountPaise}──► order-service
                         │ same checks and records as above
                         │──POST /api/internal/repair-requests/{id}/payment-confirmation──► repair-service (PAID)
                         │ commit, or roll back everything
```

`/api/repairs/{id}/quote` and `/api/repairs/{id}/approve` are short aliases of
`/api/repair-requests/{id}/quote` (or `/quote/revise`) and `/api/repair-requests/{id}/quote/approve`. They follow
the same rules and produce the same responses (docs/REPAIR_WORKFLOW.md).

---

## 3. Payment rules

| Requirement | Implementation |
|---|---|
| Unique transaction IDs | `TXN-yyyyMMdd-XXXXXXXXXX` (10 random Crockford characters from `SecureRandom`). The service checks for duplicates, and there is a unique constraint `uk_payments_transaction_id`. Orders are `ORD-…`, commission `COM-…`, payouts `PO-…` and refunds `RFD-…` |
| Methods | `UPI`, `CARD`, `NET_BANKING`, `WALLET` (`method` is required) |
| Simulated outcomes | `SUCCESS` (default), `FAILED`, `CANCELLED` |
| Incorrect amount | `amountPaise` must equal the amount owed (base + customer fee), even when simulating a failure. Otherwise **422** with `expectedAmountPaise` and `receivedAmountPaise`, and **nothing is recorded** |
| Only success confirms | Only a `SUCCESS` turns the order `CONFIRMED`, marks the product `SOLD` or moves the repair to `PAID` |
| Failed payments create no payout | `FAILED` and `CANCELLED` attempts are kept as history only, with `failureReason` and no settlement key. They create no commission, payout or ledger lines, and the order or quote stays payable |
| No duplicate processing | (1) The order or quote row is locked (`SELECT … FOR UPDATE`). (2) A second payment of a paid order or quote returns **409** with the existing `transactionId`. (3) Only successful payments get `settlement_key = ORDER-{id}` or `QUOTE-{id}`, which is unique in the database. (4) The optional `Idempotency-Key` header makes a retry return the original attempt (**200**, `Idempotent-Replay: true`) instead of creating a new one. Reusing a key for a different order is **409** |
| Cross-service consistency (audit C-4) | The product-service or repair-service confirmation is the **last** step inside the database transaction. If it fails, the payment, commission, payout, ledger and order changes all roll back, so no service is left holding a payment the other doesn't know about |
| No card or bank data | The request has no such fields, unknown JSON fields are ignored, and no column exists for them |

### Status values

| Entity | Statuses |
|---|---|
| Order | `PENDING_PAYMENT` → `CONFIRMED` → `PARTIALLY_REFUNDED` / `REFUNDED`; or `CANCELLED` (by the buyer, or when the payment window expires) |
| Payment | `SUCCESS`, `FAILED`, `CANCELLED`, `PARTIALLY_REFUNDED`, `REFUNDED` |
| Commission | `RECORDED` → `PARTIALLY_REVERSED` / `REVERSED` |
| Payout | `PENDING` → `PARTIALLY_REVERSED` / `REVERSED` (`PAID` and `ON_HOLD` are reserved for a future settlement job) |

---

## 4. Refunds and the ledger

Refunds are simulated and complete instantly (`RefundRecord` with status `COMPLETED`).

- Called by **the seller or repairer who was paid**: `POST /api/transactions/{txId}/refunds`. The buyer gets 403
  and anyone else 404.
- Called by **ReLoop support** for disputes: `POST /api/internal/transactions/{txId}/refunds` with `X-Internal-Api-Key`.
- Body: `{"baseAmountPaise"?: n, "reason": CUSTOMER_REQUEST | ITEM_NOT_AS_DESCRIBED | REPAIR_NOT_COMPLETED |
  DISPUTED_ORDER | ORDER_CANCELLED | OTHER, "note"?: "…"}`. Leave out `baseAmountPaise` to refund everything that
  is left.

`RefundCalculator` splits a refund **proportionally**:

| Refund ₹250 of a ₹1,000 sale | | Then the remaining ₹750 | |
|---|---:|---|---:|
| Customer gets back (base + fee share) | ₹255 | Customer gets back | ₹765 |
| Taken back from the provider (base − commission share) | ₹245 | Taken back from the provider | ₹735 |
| ReLoop revenue reversed (fee + commission share) | ₹10 | ReLoop revenue reversed | ₹30 |

The final refund takes exactly what is left, so rounding never leaves stray paise. A refund larger than what is
left is rejected (400 `fieldErrors.baseAmountPaise`). So is a refund of a failed or cancelled attempt (409), or of
an already refunded payment (409).

**Ledger.** Each successful payment posts four balanced lines (debits = credits):

| Account | Direction | ₹1,000 example |
|---|---|---:|
| `CUSTOMER_PAYMENTS` | DEBIT | 1,020 |
| `PROVIDER_PAYABLE` | CREDIT | 980 |
| `PLATFORM_FEE_REVENUE` | CREDIT | 20 |
| `PLATFORM_COMMISSION_REVENUE` | CREDIT | 20 |

Each refund posts the **opposite** entry on the same accounts, for that refund's share. Each reversal line has
`reversalOfEntryId` pointing at the line it reverses, and a `refundRecordId`. After a full refund every account nets
to zero (`RefundApiTest`, `LedgerServiceTest`). Payment status, commission status, payout status and the order's
refunded amount are all updated in the same transaction.

---

## 5. API reference (order-service, port 8082)

All `/api/**` calls except `/api/internal/**` need `Authorization: Bearer <JWT from product-service>`. Errors use
the shared format `{timestamp, status, error, message, path, fieldErrors?}`. Some add details such as
`transactionId`, `orderStatus`, `quoteStatus`, `paymentStatus` or `expectedAmountPaise`.

| Method & path | Who | Result |
|---|---|---|
| `POST /api/orders` `{"productId"}` | CUSTOMER (not the seller) | **201** order (reserves the product). **200** with the existing open order if the same buyer repeats the request. 403 own listing, 404 unknown, 409 not available, 503 product-service down |
| `GET /api/orders/{id}` | buyer or seller | Order with frozen fees, `amountDuePaise`, `expiresAt` and every payment attempt. 404 for anyone else |
| `GET /api/orders?role=buyer\|seller` | CUSTOMER | Your orders, newest first |
| `POST /api/orders/{id}/cancel` `{"reason"?}` | buyer | Unpaid → `CANCELLED` and the product is released. Paid → 409 (ask for a refund instead) |
| `POST /api/payments/simulate` | CUSTOMER (the order's buyer or the quote's customer) | `{"orderId" \| "repairQuoteId", "method", "outcome"?, "amountPaise"}` plus optional `Idempotency-Key` header. **201** transaction (also for FAILED and CANCELLED attempts), **200** idempotent replay, 400, 404, 409, 422, 503 |
| `GET /api/payments/{txId}` | payer or provider | Same as below (kept for older clients) |
| `GET /api/transactions/{txId}` | payer or provider | The transaction: amounts, frozen rates, `currentProviderNetPaise`, `currentPlatformRevenuePaise`, commission and payout references, refunds, `receiptUrl` |
| `GET /api/transactions/{txId}/receipt?format=html\|text&download=true\|false` | payer or provider | Printable HTML (with a Print / Save as PDF button) or plain text, labelled SIMULATED. `download=true` sends `Content-Disposition: attachment` |
| `GET /api/transactions/{txId}/ledger` | payer or provider | Ledger lines, with the refund reference on reversal lines |
| `POST /api/transactions/{txId}/refunds` | provider who was paid | §4 |
| `GET /api/customers/{id}/payments` | that customer only | Every attempt plus totals: paid, fees, refunded, net spent, and counts by status |
| `GET /api/providers/{id}/earnings` | that seller or repairer only (`{id}` = account id) | Totals (gross, commission, net, refunds, current net, pending, paid out), the same split by transaction type, and one row per payout |
| `GET /api/providers/{id}/payouts` | that seller or repairer only | Payout records with pending and paid-out totals |
| `GET /api/fee-rates` | any signed-in user | Fee rates in force per transaction type, with their source (`APPLICATION_PROPERTIES` or `DATABASE`) |
| `PUT /api/internal/fee-rates/{type}` `{"customerFeeRate","providerCommissionRate","note"?}` | ReLoop admin (`X-Internal-Api-Key`) | Database override (each rate from 0 to 0.5). Applies to **new** orders and quotes only |
| `DELETE /api/internal/fee-rates/{type}` | ReLoop admin | Remove the override and go back to application.properties |
| `POST /api/internal/transactions/{txId}/refunds` | ReLoop support | §4 |

Repair-service (8083): `POST /api/repairs/{id}/quote` (REPAIRER who owns the request) and
`POST /api/repairs/{id}/approve` (CUSTOMER who owns the request). product-service (8081):
`POST /api/internal/products/{id}/reserve | confirm-sale | release`, which only order-service calls, with the
internal key.

**Access control.** Payment, payout and earnings records are only visible to the people involved. Someone else's
order or transaction returns 404, so its existence isn't revealed. Someone else's dashboard returns 403, the wrong
role 403, and no token 401. Internal routes need the shared key and compare it in constant time.

### Dashboards stay consistent

The customer, seller and repairer dashboards all read the **same rows**. `GET /api/transactions/{txId}` returns
identical JSON to the buyer and to the provider. A provider's earnings are the sum of their payout records, and each
payout carries the same gross, commission and net as its payment. `DashboardAndReceiptApiTest` asserts this across a
purchase, a failed attempt, a repair booking and a refund.

---

## 6. Try it (all three services running)

```bash
# terminal 1-3 (see README): product-service 8081, order-service 8082, repair-service 8083
J='Content-Type: application/json'
login() { curl -s localhost:8081/api/auth/login -H "$J" -d "{\"email\":\"$1\",\"password\":\"$2\"}" | jq -r .accessToken; }
ASHA=$(login customer.demo@reloop.local 'ReLoopDemo#Cust2026')     # buyer
NISHA=$(login nisha.demo@reloop.local 'ReLoopDemo#Nisha2026')      # seller
NISHA_ID=$(curl -s localhost:8081/api/auth/me -H "Authorization: Bearer $NISHA" | jq .id)
ASHA_ID=$(curl -s localhost:8081/api/auth/me -H "Authorization: Bearer $ASHA" | jq .id)

# 1. Nisha lists a ₹1,000 item
PID=$(curl -s localhost:8081/api/products -H "Authorization: Bearer $NISHA" -H "$J" -d '{
  "title":"Philips mixer grinder","description":"750 W, 3 jars","category":"APPLIANCES",
  "price":1000,"condition":"GOOD","city":"Ahmedabad"}' | jq .id)

# 2. Asha orders it: the product becomes RESERVED and the order shows 102000 paise to pay
ORDER=$(curl -s localhost:8082/api/orders -H "Authorization: Bearer $ASHA" -H "$J" -d "{\"productId\":$PID}")
echo "$ORDER" | jq '{orderNumber,status,subtotalPaise,customerFeePaise,totalPaise,sellerNetPaise,platformRevenuePaise}'
OID=$(echo "$ORDER" | jq .id)
curl -s localhost:8081/api/products/$PID | jq .status                       # "RESERVED"

# 3. Wrong amount → 422; a declined card → FAILED (nothing charged, still payable)
curl -s localhost:8082/api/payments/simulate -H "Authorization: Bearer $ASHA" -H "$J" \
  -d "{\"orderId\":$OID,\"method\":\"UPI\",\"amountPaise\":100000}" | jq '{status,message,expectedAmountPaise}'
curl -s localhost:8082/api/payments/simulate -H "Authorization: Bearer $ASHA" -H "$J" \
  -d "{\"orderId\":$OID,\"method\":\"CARD\",\"outcome\":\"FAILED\",\"amountPaise\":102000}" | jq '{status,failureReason}'

# 4. Pay for real (simulated), with an idempotency key; repeating it returns the same transaction (200)
PAY="{\"orderId\":$OID,\"method\":\"UPI\",\"outcome\":\"SUCCESS\",\"amountPaise\":102000}"
TX=$(curl -s localhost:8082/api/payments/simulate -H "Authorization: Bearer $ASHA" -H "$J" \
  -H 'Idempotency-Key: demo-checkout-1' -d "$PAY" | jq -r .transactionId)
curl -si localhost:8082/api/payments/simulate -H "Authorization: Bearer $ASHA" -H "$J" \
  -H 'Idempotency-Key: demo-checkout-1' -d "$PAY" | grep -i -E '^HTTP|Idempotent-Replay'
curl -s localhost:8082/api/payments/simulate -H "Authorization: Bearer $ASHA" -H "$J" -d "$PAY" | jq .message   # 409 already paid
curl -s localhost:8081/api/products/$PID | jq .status                       # "SOLD"

# 5. Same numbers for both sides, receipts, ledger
curl -s localhost:8082/api/transactions/$TX -H "Authorization: Bearer $NISHA" | \
  jq '{transactionId,status,totalChargedPaise,providerCommissionPaise,providerNetPaise,platformRevenuePaise,payoutReference}'
curl -s "localhost:8082/api/transactions/$TX/receipt?format=text" -H "Authorization: Bearer $ASHA"
curl -s "localhost:8082/api/transactions/$TX/receipt" -H "Authorization: Bearer $ASHA" -o receipt.html  # open, then Print
curl -s localhost:8082/api/transactions/$TX/ledger -H "Authorization: Bearer $ASHA" | jq '.[] | {account,direction,amountPaise}'

# 6. Dashboards (403 for anyone else's id)
curl -s localhost:8082/api/customers/$ASHA_ID/payments -H "Authorization: Bearer $ASHA" | jq '{count,successfulCount,failedCount,totalPaidPaise,totalFeesPaise}'
curl -s localhost:8082/api/providers/$NISHA_ID/earnings -H "Authorization: Bearer $NISHA" | jq .totals
curl -s localhost:8082/api/providers/$NISHA_ID/payouts  -H "Authorization: Bearer $NISHA" | jq '.payouts[0]'
curl -si localhost:8082/api/providers/$NISHA_ID/earnings -H "Authorization: Bearer $ASHA" | head -1   # 403

# 7. Nisha refunds ₹250, then the rest; the ledger nets to zero
curl -s localhost:8082/api/transactions/$TX/refunds -H "Authorization: Bearer $NISHA" -H "$J" \
  -d '{"baseAmountPaise":25000,"reason":"ITEM_NOT_AS_DESCRIBED","note":"Lid cracked"}' | \
  jq '{status,refundedAmountPaise,currentProviderNetPaise,currentPlatformRevenuePaise}'
curl -s localhost:8082/api/transactions/$TX/refunds -H "Authorization: Bearer $NISHA" -H "$J" \
  -d '{"reason":"CUSTOMER_REQUEST"}' | jq '{status,refundedAmountPaise,currentProviderNetPaise}'

# 8. Fees are configuration: change the marketplace rates for NEW orders only, then reset
curl -s localhost:8082/api/fee-rates -H "Authorization: Bearer $ASHA" | jq
curl -s -X PUT localhost:8082/api/internal/fee-rates/MARKETPLACE_PURCHASE -H "$J" \
  -H 'X-Internal-Api-Key: reloop-local-dev-internal-key-change-me' \
  -d '{"customerFeeRate":0.03,"providerCommissionRate":0.03,"note":"demo"}' | jq
curl -s -X DELETE localhost:8082/api/internal/fee-rates/MARKETPLACE_PURCHASE \
  -H 'X-Internal-Api-Key: reloop-local-dev-internal-key-change-me' | jq .source
```

**Repair booking** (repairer demo account; the request must be `INSPECTED`. See docs/REPAIR_WORKFLOW.md for
booking, accepting and inspecting):

```bash
REP=$(login repairer.demo@reloop.local 'ReLoopDemo#Rep2026')
curl -s -X POST localhost:8083/api/repairs/$ID/quote -H "Authorization: Bearer $REP" -H "$J" \
  -d '{"amount":1500,"summary":"Replace OLED panel","estimatedDays":2}' | jq '.status, .quote'
QUOTE=$(curl -s localhost:8083/api/repair-requests/$ID -H "Authorization: Bearer $ASHA" | jq .quote.quoteId)
curl -s -X POST localhost:8083/api/repairs/$ID/approve -H "Authorization: Bearer $ASHA" | jq .status   # AWAITING_PAYMENT
curl -s localhost:8082/api/payments/simulate -H "Authorization: Bearer $ASHA" -H "$J" \
  -d "{\"repairQuoteId\":$QUOTE,\"method\":\"WALLET\",\"amountPaise\":153000}" | \
  jq '{transactionId,transactionType,totalChargedPaise,providerNetPaise,platformRevenuePaise}'   # 153000 / 147000 / 6000
curl -s localhost:8083/api/repair-requests/$ID -H "Authorization: Bearer $ASHA" | jq .status       # PAID
```

**Troubleshooting**

- *Schema errors on startup after upgrading.* The dev H2 files use `ddl-auto=update`, and new NOT NULL columns
  can't be added to old rows. Stop the services and delete `order-service/data/` (and `product-service/data/` if
  product-service complains). The demo data is seeded again.
- *503 from `POST /api/orders` or a payment.* product-service or repair-service isn't running, or the
  `RELOOP_INTERNAL_API_KEY` values differ between services.
- *410 from `POST /api/products/{id}/purchase`.* Expected: buy through order-service. Set
  `RELOOP_DIRECT_PURCHASE_ENABLED=true` on product-service only for the old flow.

---

## 7. Tests

`order-service/src/test/java/com/reloop/orderpayment/`:

| Test | Covers |
|---|---|
| `service/PaymentServiceTest` (unit, Mockito) | **successful payment**, **failed payment**, **cancelled payment**, **incorrect amount**, **duplicate transaction**, idempotent replay, expired or cancelled order, exactly one target, repair payment copying the quote's frozen rates |
| `service/CommissionCalculationTest`, `service/FeeCalculatorTest` (unit) | **commission calculation**, **customer fee**, **provider net earnings**, rounding table, rate bounds, ₹ formatting |
| `service/RefundCalculatorTest` (unit) | **refund calculation**: full, partial, remainder, rounding over 3 refunds, invalid amounts |
| `service/LedgerServiceTest` (unit) | balanced payment lines; reversal lines linked to originals; zero balance after a full refund |
| `payment/MarketplaceOrderApiTest` | order creation with frozen fees, reuse of the open order, product-service errors passed on, roles, buyer and seller visibility, cancel and release, expiry sweep and release retry |
| `payment/MarketplacePaymentApiTest` | success with commission, payout and ledger; failed and cancelled; every method; 422 amounts; duplicates; Idempotency-Key; rollback when product-service refuses; card data never stored |
| `payment/RefundApiTest` | partial then full refund, ledger reversal and balances; over-refund; who may refund; repair refunds; failed attempts can't be refunded |
| `payment/DashboardAndReceiptApiTest` | consistent customer, seller and repairer numbers; refunds reflected; 401/403/404 protection; HTML and text receipts; HTML escaping |
| `payment/FeeRateApiTest` | properties defaults; database override for new orders only; internal key and validation |
| `repairquote/PaymentApiTest`, `repairquote/RepairQuoteApiTest` | repair quotes and REPAIR_BOOKING payments, rollback when repair-service refuses or is down |
| `repository/PaymentConstraintTest` | unique transaction id, unique settlement key, unique order number, exactly-one-target CHECK |

product-service: `marketplace/InternalProductSaleTest` (reserve, confirm-sale, release, key, own listing) and
`marketplace/DirectPurchaseDisabledTest` (410). repair-service: `RepairRequestFlowTest` covers the `/api/repairs/{id}/quote|approve` aliases, and
`HttpOrderServiceClientTest` checks that `repairerAccountId` is sent.

```bash
mvn -B -ntp -f order-service/pom.xml verify
mvn -B -ntp -f product-service/pom.xml verify
mvn -B -ntp -f repair-service/pom.xml verify
```

API tests replace product-service and repair-service with Mockito mocks (`support/ApiTestBase`) and empty the
in-memory database before each test.

## Not yet

Real gateway integration (it would plug in behind `PaymentService` with webhooks instead of a caller-chosen
outcome). Payout settlement (`PENDING` → `PAID`) and bank transfers. GST invoices. An admin UI for fee rates and
disputes. Carts with several items per order (the schema supports items; the API takes one product per order).
