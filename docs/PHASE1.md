# ReLoop — Phase 1: Foundation

Three independently runnable Spring Boot services, each with its own H2 database
and health endpoints.

| Service | Port | Package | H2 database file |
|---|---|---|---|
| `product-service` (Product & Account) | 8081 | `com.reloop.product` | `product-service/data/reloop_product` |
| `order-service` (Order & Payment) | 8082 | `com.reloop.order` | `order-service/data/reloop_order` |
| `repair-service` (Repair & Tracking) | 8083 | `com.reloop.repair` | `order-service` → see `repair-service/data/reloop_repair` |

## Prerequisites

- Java 21 (`java -version`)
- Maven 3.9+ (`mvn -version`)

## Run commands

Open three terminals from the repository root:

```bash
# Terminal 1
mvn -f product-service/pom.xml spring-boot:run
# Terminal 2
mvn -f order-service/pom.xml spring-boot:run
# Terminal 3
mvn -f repair-service/pom.xml spring-boot:run
```

Build + test all three (same command CI runs):

```bash
mvn -B -ntp -f product-service/pom.xml verify
mvn -B -ntp -f order-service/pom.xml verify
mvn -B -ntp -f repair-service/pom.xml verify
```

## API endpoints (Phase 1)

| Method | URL | Purpose |
|---|---|---|
| GET | `http://localhost:8081/api/health` | Product service health |
| GET | `http://localhost:8082/api/health` | Order service health |
| GET | `http://localhost:8083/api/health` | Repair service health |
| GET | `http://localhost:808X/actuator/health` | Actuator health (detailed, includes DB status) |
| GET | `http://localhost:808X/h2-console` | H2 web console (JDBC URL: `jdbc:h2:file:./data/reloop_*`, user `sa`, empty password) |

## Test instructions

```bash
mvn -f product-service/pom.xml test
```

Each service ships 6 tests:

1. `ProductServiceApplicationTests.contextLoads` — full Spring context starts.
2. `DatabaseConnectionTest.h2InMemoryDatabaseIsReachable` — real JDBC connection to in-memory H2.
3. `HealthControllerTest.apiHealthReturnsUpWithServiceName` — `GET /api/health` → 200, correct JSON.
4. `HealthControllerTest.actuatorHealthIsExposed` — `GET /actuator/health` → 200.
5. `HealthControllerSliceTest.healthReturnsWhatTheServiceProvides` — controller delegates to mocked service.
6. `HealthControllerSliceTest.healthServiceIsCalledExactlyOnce` — Mockito verification.

Tests always run against in-memory H2 (`src/test/resources/application.yml` overrides the main config),
so demo data files are never touched.

## Expected output

- `mvn verify` ends with `BUILD SUCCESS` and `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0` per service.
- `curl http://localhost:8081/api/health` returns `{"service":"product-service","status":"UP"}`.
- GitHub Actions workflow **CI** builds all three services on every push.

## Known limitations (Phase 1)

- No authentication, roles, products, orders, repairs or payments yet — those arrive in Phases 2–4.
- No API gateway yet; the frontend must call the three ports directly for now.
- H2 file databases are created relative to the working directory of each service.
- Local compilation could not be executed inside the authoring sandbox (no route to Maven Central);
  compilation and tests are verified by the GitHub Actions run on this branch.

## Next recommended task

**Phase 2 — Accounts and marketplace**: registration/login with separate CUSTOMER and REPAIRER
flows, role-based authorization, product listings and the customer dashboard in `product-service`.
