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
| Order & Payment | 8082 | `com.reloop.orderpayment` | H2 `reloop_order` | **PC2** | `POST /api/orders`, `POST /api/repairs/{id}/quote`, `POST /api/repairs/{id}/approve`, `POST /api/payments/simulate`, `GET /api/transactions/{id}` |
| Repair & Tracking | 8083 | `com.reloop.repair` | H2 `reloop_repair` | **PC3** | `POST /api/repairer-profiles` (internal), `GET /api/repairer-profiles/{id}`, `GET /api/repairers`, `POST /api/repair-requests`, `GET/POST /api/repairers/{id}/inventory`, `PATCH /api/repair-requests/{id}/status` |

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
4. Repair lifecycle: `REQUESTED → ACCEPTED → INSPECTED → QUOTED → APPROVED → PAID →
   IN_PROGRESS → COMPLETED`; quote approval is mandatory before payment; work starts
   only after successful simulated payment.
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
