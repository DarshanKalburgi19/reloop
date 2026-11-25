# ReLoop

**ReLoop** is a circular-commerce platform where customers buy and resell second-hand
products and book repairs from independent repair professionals.

This repository is a **monorepo** containing three independently runnable Spring Boot
microservices plus (later) the shared frontend.

## Services

| Service | Port | Maven project | Java package | Owner | Purpose |
|---|---|---|---|---|---|
| Product & Account | 8081 | `product-service/` | `com.reloop.product` | PC1 | Registration, login, customer/repairer account separation, product listings, customer dashboard |
| Order & Payment | 8082 | `order-service/` | `com.reloop.orderpayment` | PC2 | Purchase orders, repair quotes/approvals, simulated payments, commission ledger |
| Repair & Tracking | 8083 | `repair-service/` | `com.reloop.repair` | PC3 | Repairer profiles, locations, repair booking/status, inventory, repairer dashboard |

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

**Phase 1 — Foundation (complete):** three runnable services, H2 databases, actuator
health checks, 18 passing tests (6 per service).

Roadmap: Phase 2 accounts & marketplace → Phase 3 repairer system → Phase 4 orders &
simulated payments → Phase 5 frontend integration → Phase 6 testing & deployment.

## Documentation

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — initial architecture, API contracts, ownership
- [docs/PHASE1.md](docs/PHASE1.md) — Phase 1 runbook, endpoints, test instructions

## Business rules (implemented in later phases)

- Customer platform fee 2%, seller/repairer commission 2% — both **configurable**, never hard-coded.
- All monetary math uses `BigDecimal` / integer paise; payments are simulations only.
- No real card numbers, CVV, UPI PINs or banking credentials are ever collected.
- Earnings are never computed by summing payments — separate transaction, commission
  and payout records are kept so refunds/cancellations stay correct.
