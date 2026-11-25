# ReLoop — Initial Architecture

Status: **Phase 1 — Foundation**. This document records the agreed structure before
any business features are implemented.

## 1. System overview

```
                         ┌──────────────────────────┐
                         │   Frontend (Phase 5)     │
                         │  /frontend  (HTML/CSS/JS)│
                         └────────────┬─────────────┘
                                      │ REST / HTTP
              ┌───────────────────────┼───────────────────────┐
              │                       │                       │
              ▼                       ▼                       ▼
   ┌─────────────────────┐ ┌─────────────────────┐ ┌─────────────────────┐
   │ product-service     │ │ order-service       │ │ repair-service      │
   │ com.reloop.product  │ │ com.reloop.         │ │ com.reloop.repair   │
   │ :8081               │ │   orderpayment      │ │ :8083               │
   │                     │ │ :8082               │ │                     │
   │ Accounts, products, │ │ Orders, quotes,     │ │ Repairer profiles,  │
   │ listings, dashboard │ │ simulated payments, │ │ bookings, inventory,│
   │                     │ │ commission ledger   │ │ status tracking     │
   └──────────┬──────────┘ └──────────┬──────────┘ └──────────┬──────────┘
              │                       │                       │
              ▼                       ▼                       ▼
        H2: reloop_product      H2: reloop_order        H2: reloop_repair
        (own schema)            (own schema)            (own schema)
```

## 2. Service catalogue and ownership

| Service | Port | Package | Database | Owner | Planned main endpoints (blueprint contract) |
|---|---|---|---|---|---|
| Product & Account | 8081 | `com.reloop.product` | H2 `reloop_product` | **PC1** | `POST /api/auth/register/customer`, `POST /api/auth/register/repairer`, `POST /api/auth/login`, `GET /api/auth/me` (see [AUTH.md](AUTH.md)), `GET/POST /api/products`, `GET/PUT/DELETE /api/products/{id}`, `GET /api/products/{id}/passport`, `POST /api/products/{id}/purchase`, `GET /api/products/mine` (see [MARKETPLACE.md](MARKETPLACE.md)) |
| Order & Payment | 8082 | `com.reloop.orderpayment` | H2 `reloop_order` | **PC2** | `POST /api/internal/repair-quotes`, `POST /api/internal/repair-quotes/{id}/approve\|reject` (internal, called by repair-service), `POST /api/payments/simulate`, `GET /api/payments/{transactionId}` (see [REPAIR_WORKFLOW.md](REPAIR_WORKFLOW.md)); planned: `POST /api/orders` |
| Repair & Tracking | 8083 | `com.reloop.repair` | H2 `reloop_repair` | **PC3** | `POST /api/repairer-profiles` (internal), `GET /api/repairer-profiles/{id}`, `POST/GET /api/repairers`, `GET/PUT /api/repairers/{id}`, `GET /api/repairers/nearby`, `GET /api/repairers/me` (see [REPAIRERS.md](REPAIRERS.md)), `POST /api/repair-requests`, `GET /api/repair-requests/mine\|incoming\|{id}\|{id}/history`, `POST /api/repair-requests/{id}/accept\|reject\|inspect\|quote\|quote/revise\|quote/approve\|quote/reject\|progress\|complete\|cancel`, `PATCH /api/repair-requests/{id}/status`, `POST /api/internal/repair-requests/{id}/payment-confirmation` (internal) (see [REPAIR_WORKFLOW.md](REPAIR_WORKFLOW.md)), `GET/POST /api/inventory/parts`, `GET/PATCH /api/inventory/parts/{id}`, `POST /api/inventory/parts/{id}/stock-in\|use\|adjust\|discontinue\|reactivate`, `GET /api/inventory/parts/{id}/movements`, `GET /api/inventory/movements\|low-stock\|summary`, `GET /api/inventory/repair-requests/{id}/parts` (REPAIRER, own stock only; see [INVENTORY.md](INVENTORY.md)) |

Shared (all three, Phase 1): `GET /api/health` and `GET /actuator/health`.

Non-coding roles: test cases & defect log · startup policy / business model /
commission research · cloud documentation, presentation, demo video.

## 3. Monorepo layout

- Root holds one Maven project per service — three genuinely separate Spring Boot
  applications, not modules of one build. Each can be built, run and deployed alone:
  `mvn -f product-service/pom.xml spring-boot:run`.
- `docs/` — architecture and phase runbooks.
- `.github/workflows/ci.yml` — CI builds all three on every push (Java 21, Maven).
- `frontend/` — added in Phase 5.
- `data/` (git-ignored) — H2 file databases, created at runtime relative to the
  working directory of each service.

## 4. How the services communicate

1. **Phase 1:** no cross-service calls yet — services are independent so each
   developer can work without blocking the others.
   **Phase 3 (auth):** first call — product-service → repair-service
   `POST /api/repairer-profiles` during repairer registration, authenticated with the shared
   `X-Internal-Api-Key` header (see [AUTH.md](AUTH.md)).
   **Repairer module:** the browser calls repair-service directly with the JWT from
   product-service. repair-service verifies it locally with the shared HS256 secret and issuer;
   it never calls product-service to check tokens (see [REPAIRERS.md](REPAIRERS.md)).
   **Repair workflow:** repair-service → order-service to issue, approve and reject quotes
   (`/api/internal/repair-quotes…`); order-service → repair-service to confirm a payment
   (`/api/internal/repair-requests/{id}/payment-confirmation`). Both directions use
   `X-Internal-Api-Key`. order-service owns the quote amounts and payments; repair-service owns the request
   and its history and keeps only a snapshot of the current quote (see [REPAIR_WORKFLOW.md](REPAIR_WORKFLOW.md)).
2. **Later phases:** synchronous **REST over HTTP** between services using only the
   agreed endpoint contracts above. The frontend calls the same REST APIs.
3. **Database-per-service:** each service owns its schema. Services never read
   another service's tables; they ask over REST instead. This keeps the microservice
   boundary real and lets H2 be swapped for PostgreSQL service-by-service.
4. **Service discovery** is out of scope for the MVP: URLs are fixed
   (`http://localhost:8081` … 8083), later replaced by Docker Compose service names.

## 5. Technology choices

| Concern | Choice | Notes |
|---|---|---|
| Language / runtime | Java 21 | LTS |
| Framework | Spring Boot 3.5.16 | Web, Data JPA, Actuator; **no Lombok** |
| Build | Maven, one project per service | Independent versioning/deploy |
| Database | H2 now, PostgreSQL later | Same JPA code, swapped via config |
| Tests | JUnit 5 + Mockito + MockMvc | 18 tests in Phase 1 (6 per service) |
| CI | GitHub Actions | `mvn verify` per service, test totals reported as commit statuses |
| Frontend | HTML, CSS, Bootstrap, JS | Phase 5 |
| Maps / QR | Leaflet + OpenStreetMap / ZXing | Phase 3+ |

## 6. Phase 1 health contract

Every service exposes:

- `GET /api/health` → `{"service":"<name>","status":"UP"}` — stable app-level URL
  for the frontend and sibling services.
- `GET /actuator/health` → standard Actuator payload incl. component/DB details
  (`show-details=always` for the demo).

## 7. Rules that later phases must respect

1. Two account types only (`CUSTOMER`, `REPAIRER`), separate registration flows and
   dashboards — no account conversion path.
2. Fees/commissions configurable (2% + 2% for the demo), stored per transaction;
   `BigDecimal`/integer-paise arithmetic.
3. Payments are simulations; success, failure and cancellation have distinct
   outcomes; failed payments never confirm orders or create payouts; refunds reverse
   ledger entries.
4. Repair lifecycle: `REQUESTED → ACCEPTED → INSPECTED → QUOTED → QUOTE_APPROVED →
   AWAITING_PAYMENT → PAID → IN_PROGRESS → COMPLETED` (plus `REJECTED`, `QUOTE_REJECTED`,
   `CANCELLED`). Quote approval is mandatory before payment; a revised quote needs fresh approval;
   work starts only after successful simulated payment (implemented: [REPAIR_WORKFLOW.md](REPAIR_WORKFLOW.md)).
5. Role-based authorization: customers cannot touch repairer inventory; repairers
   cannot see other repairers' earnings.
6. Earnings are read from transaction/commission/payout records — never by summing
   successful payments.

## 8. Build sequence (from the master instruction)

1. **Foundation** ← you are here (three services, health endpoints, CI)
2. Accounts & marketplace
3. Repairer system
4. Orders & simulated payments
5. Frontend integration
6. Testing & deployment (30+ test cases, Docker, cloud)
