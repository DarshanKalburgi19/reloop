# ReLoop

**ReLoop** is a circular-commerce platform where customers buy and resell second-hand
products and book repairs from independent repair professionals.

This repository is a **monorepo** containing three independently runnable Spring Boot
microservices plus the shared web frontend (`frontend/`, see [docs/FRONTEND.md](docs/FRONTEND.md)).

## Services

| Service | Port | Maven project | Java package | Owner | Purpose |
|---|---|---|---|---|---|
| Product & Account | 8081 | `product-service/` | `com.reloop.product` | PC1 | Registration, login, customer/repairer account separation, product listings, customer dashboard |
| Order & Payment | 8082 | `order-service/` | `com.reloop.orderpayment` | PC2 | Purchase orders, repair quotes/approvals, simulated payments, commission ledger |
| Repair & Tracking | 8083 | `repair-service/` | `com.reloop.repair` | PC3 | Repairer profiles, locations, repair booking/status, inventory, simulated delivery tracking, repairer dashboard |

**Account rule:** there are exactly two account types — `CUSTOMER` and `REPAIRER`.
They are registered through separate flows; a customer can never convert an account
into a repairer account.

**Communication:** services call each other synchronously over REST/HTTP using the
endpoint contracts in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Each service owns
its own H2 database — no shared tables, no cross-service joins.

## Tech stack

- Java 21 · Spring Boot 3.5.16 · Maven · Spring Data JPA
- H2 (local MVP; PostgreSQL after integration)
- HTML/CSS/Bootstrap/JavaScript frontend (Phase 5) · Leaflet + OpenStreetMap · ZXing
- JUnit 5 + Mockito · Docker Compose (Phase 6) · GitHub Actions CI

## Prerequisites

- Java 21 (`java -version`)
- Maven 3.9+ (`mvn -version`)

## Run all three services

### PowerShell (Windows)

```powershell
# Terminal 1 — Product & Account Service
mvn -f product-service/pom.xml spring-boot:run

# Terminal 2 — Order & Payment Service
mvn -f order-service/pom.xml spring-boot:run

# Terminal 3 — Repair & Tracking Service
mvn -f repair-service/pom.xml spring-boot:run
```

Verify health endpoints (PowerShell):

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health   # product
Invoke-RestMethod http://localhost:8082/actuator/health   # order
Invoke-RestMethod http://localhost:8083/actuator/health   # repair

Invoke-RestMethod http://localhost:8081/api/health        # app-level health
Invoke-RestMethod http://localhost:8082/api/health
Invoke-RestMethod http://localhost:8083/api/health
```

### Bash / macOS / Linux

```bash
mvn -f product-service/pom.xml spring-boot:run   # terminal 1
mvn -f order-service/pom.xml spring-boot:run     # terminal 2
mvn -f repair-service/pom.xml spring-boot:run    # terminal 3

curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
```

## Build and test everything

```bash
mvn -B -ntp -f product-service/pom.xml verify
mvn -B -ntp -f order-service/pom.xml verify
mvn -B -ntp -f repair-service/pom.xml verify
```

GitHub Actions (`.github/workflows/ci.yml`) runs the same three builds on every push.

## Project status

- **Foundation (complete):** three runnable services, H2 databases, actuator health checks.
- **Data model (complete):** entities, constraints and indexes for all three services ([ER_DIAGRAM](docs/ER_DIAGRAM.md), [SCHEMA](docs/SCHEMA.md)).
- **Accounts & auth (complete):** customer and repairer registration, login, JWT, role-based access, demo accounts ([AUTH](docs/AUTH.md)).
- **Second-hand marketplace (complete):** list, edit, deactivate, browse, keyword search, filters,
  product details, purchase rules, and a product passport with full history. Demo listings in
  Ahmedabad, Surat, Vadodara, Rajkot and Gandhinagar ([MARKETPLACE](docs/MARKETPLACE.md)).
- **Repairer service providers (complete):** repairers create and edit workshop profiles
  (categories, expertise, priced services, location, hours, repair methods, availability).
  Customers search by city, category, price and method, or find workshops near a map point
  (Leaflet-ready coordinates). JWT-secured in repair-service. Ten labelled demo workshops across
  Gujarat ([REPAIRERS](docs/REPAIRERS.md)).
- **Repair booking workflow (complete):** customers book a workshop (category, problem, demo photos,
  in-person or send-item), approve or reject quotes and follow a timestamped status history; repairers
  accept/reject, inspect, quote and revise, record progress and complete. Quotes, fees (2% + 2%, frozen
  per quote) and simulated payments live in order-service, which separately records commission and
  payout; the two services coordinate only over REST ([REPAIR_WORKFLOW](docs/REPAIR_WORKFLOW.md)).
- **Spare-parts inventory (complete):** each repairer manages their own parts (name, category, quantity,
  purchase cost, low-stock threshold). Every stock change is recorded as a movement: stock received,
  used in a repair (linked to a paid or in-progress repair request), or adjusted with a reason.
  Quantities can never go negative (row locks, CHECK constraint), and only the owner can change them.
  Low-stock alerts. Inventory value (quantity × cost) is reported separately from repair earnings
  ([INVENTORY](docs/INVENTORY.md)).
- **Orders, commission and simulated payments (complete):** marketplace orders reserve the listing in
  product-service. Payments for orders (`MARKETPLACE_PURCHASE`) and approved repair quotes (`REPAIR_BOOKING`)
  are simulated with UPI, CARD, NET_BANKING or WALLET, and the caller chooses SUCCESS, FAILED or CANCELLED. Fees
  are configurable (2% customer fee plus 2% provider commission, frozen per order, quote and payment):
  ₹1,000 → customer pays ₹1,020, provider nets ₹980, ReLoop keeps ₹40. Every successful payment has a unique
  `TXN-…` id and separate commission, payout and balanced ledger records. The wrong amount is refused (422) and
  duplicate payments are blocked. Refunds are proportional and reverse the ledger. Receipts can be printed or
  downloaded. Customer, seller and repairer dashboards read the same records. No real money, gateway, or card or
  bank data ([PAYMENTS](docs/PAYMENTS.md)).
- **Delivery tracking (complete, simulated):** store-to-customer (paid marketplace orders), customer-to-repairer
  and repairer-to-customer legs. Each has a `TRK-SIM-…` tracking ID, origin and destination taken from 16 predefined
  Gujarat demo locations, a simulated current position, a status (`BOOKED → PICKED_UP → IN_TRANSIT →
  NEAR_DESTINATION → DELIVERED`), an estimated arrival and a saved coordinate history. The marker moves only when the
  demo "advance" button is pressed. Both parties see identical data. A Leaflet + OpenStreetMap demo page is at
  `http://localhost:8083/tracking-demo.html`. No GPS or courier integration, and every response says so
  ([DELIVERY_TRACKING](docs/DELIVERY_TRACKING.md)).

- **Web frontend (complete):** static HTML, CSS, Bootstrap 5 and vanilla JS in `frontend/`, served by a
  dependency-free Node dev server that proxies `/api/**` to the three services (`cd frontend && npm start` →
  http://localhost:8080). It has a homepage, marketplace, product pages, checkout with the full fee breakdown,
  a Leaflet map for finding repairers, workshop pages with repair requests, login and both registrations. The
  customer dashboard covers buying, selling, listings, purchases, sales, repairs, payments, tracking and profile.
  The repairer dashboard covers overview with a profile-completion prompt, jobs, services, inventory, location,
  earnings, payments and profile. `npm run start:mock` runs it against an in-memory mock for UI work without Java
  ([FRONTEND](docs/FRONTEND.md)).

Next: reviews → testing & deployment.

## Documentation

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — initial architecture, API contracts, ownership
- [docs/ER_DIAGRAM.md](docs/ER_DIAGRAM.md) — Mermaid ER diagrams for all three services
- [docs/SCHEMA.md](docs/SCHEMA.md) — schema configuration, constraints, indexes, money conventions
- [docs/AUTH.md](docs/AUTH.md) — registration, login, JWT + roles, **demo credentials**, how to test both account types
- [docs/MARKETPLACE.md](docs/MARKETPLACE.md) — marketplace API, listing/purchase rules, product passport, demo products, curl walkthrough
- [docs/REPAIRERS.md](docs/REPAIRERS.md) — repairer workshop profiles, search, nearby/map API, authorization, demo workshops
- [docs/REPAIR_WORKFLOW.md](docs/REPAIR_WORKFLOW.md) — repair booking statuses and rules, quote/payment contracts between services, curl walkthrough, tests
- [docs/INVENTORY.md](docs/INVENTORY.md) — repairer spare-parts inventory: model, stock rules, low-stock alerts, valuation, API, curl walkthrough, tests
- [docs/PAYMENTS.md](docs/PAYMENTS.md) — orders, fees and commission, simulated payments, refunds and ledger, receipts, dashboards, API, curl walkthrough, tests
- [docs/DELIVERY_TRACKING.md](docs/DELIVERY_TRACKING.md) — simulated delivery tracking: three legs, statuses, demo locations, ETA, history, access rules, API, Leaflet/OSM frontend integration, curl walkthrough, tests
- [docs/FRONTEND.md](docs/FRONTEND.md) — web frontend: how to run it (real services or mock), page map, profile-completion prompt, modules, fee display, security notes
- [docs/PHASE1.md](docs/PHASE1.md) — Phase 1 runbook, endpoints, test instructions

## Business rules (implemented; see docs/PAYMENTS.md)

- Customer platform fee 2%, seller/repairer commission 2% — both **configurable**, never hard-coded.
- All monetary math uses `BigDecimal` / integer paise; payments are simulations only.
- No real card numbers, CVV, UPI PINs or banking credentials are ever collected.
- Earnings are never computed by summing payments — separate transaction, commission
  and payout records are kept so refunds/cancellations stay correct.
