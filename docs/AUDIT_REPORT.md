# ReLoop repository audit — 2026-11-25

- **Scope:** branch `arena/45de6a09-reloop` at commit **540425d** (18 commits since `main`'s base f40143a).
- **Method:** I read the code and configuration and compared the REST contracts field by field. Build and tests ran on GitHub Actions (details in §7).
- **Changes:** none. This file is the only addition.

## How the build was run (read first)

| What | How | Result |
|---|---|---|
| Maven build + tests, all 3 services | GitHub Actions run [45712350871](https://github.com/DarshanKalburgi19/reloop/actions/runs/45712350871), started manually for this audit. Ubuntu, Temurin JDK 21, `mvn -B -ntp -f <service>/pom.xml verify` | **all 3 succeeded** |
| Local build in the audit sandbox | **Not possible.** There is no JDK, and Maven Central and Adoptium are blocked (HTTP 000) | not run |
| Running the 3 services together, curl walkthroughs | **Not done.** It needs a JVM | not run |

Test totals come from each service's surefire reports, which CI posts as commit statuses. I can't download the full CI logs from the sandbox (the log storage host is blocked), so this report can't list compiler *warnings*. A failed compile would have failed `verify`, and it didn't.

---

## 1. Folder and file structure

340 tracked files: 296 Java, 9 docs, 3 POMs, 6 property files, 14 SVG/README demo assets, CI, `.gitignore`.

```
reloop/
├── .github/workflows/ci.yml        matrix build: mvn verify per service, test totals → commit statuses
├── .gitignore                      target/, data/, *.mv.db, IDE files
├── README.md
├── docs/  ARCHITECTURE · AUTH · ER_DIAGRAM · SCHEMA · MARKETPLACE · REPAIRERS · REPAIR_WORKFLOW · PHASE1 · AUDIT_REPORT
├── product-service/   (com.reloop.product, :8081)
│   ├── pom.xml
│   └── src/main/java/com/reloop/product/
│       ├── ProductServiceApplication
│       ├── client/      CreateRepairerProfileCommand, HttpRepairerProfileClient, RepairerProfileClient, RepairerProfileView
│       ├── config/      SecurityConfig
│       ├── controller/  AuthController, CustomerAreaController, ProductController, RepairerAreaController
│       ├── dto/auth/    9 records (registration, login, account views, ValidationPatterns)
│       ├── dto/product/ 12 records (create/update/search/passport/purchase/page)
│       ├── entity/      Account, Address, BaseEntity, City, CustomerAccount, Product, ProductHistoryEvent,
│       │                ProductImage, ProductPassport, RepairerAccountIdentity, Store
│       ├── enums/ (11)  exception/ (13 incl. GlobalExceptionHandler, ApiError)  health/ (2)
│       ├── repository/ (10)  security/ AuthenticatedAccount, JsonErrorWriter, JwtAuthenticationFilter, JwtService
│       ├── seed/        DemoAccountSeeder, DemoAccounts, DemoProductSeeder
│       └── service/     AuthService, Money, PassportCodeGenerator, PlaceholderImages, ProductMapper,
│                        ProductService, ProductSpecifications
│   src/main/resources/  application.properties, static/demo-images/placeholders/*.svg (8)
│   src/test/java/…      auth/ (AuthApiTest, RoleBasedAccessTest, support), client/, health/, marketplace/ (3 + support),
│                        repository/, security/, seed/ (2), service/ (2), app + DB smoke tests      → 113 tests
├── order-service/   (com.reloop.orderpayment, :8082)
│   └── src/main/java/com/reloop/orderpayment/
│       ├── OrderServiceApplication
│       ├── client/      HttpRepairServiceClient, PaymentConfirmation, RepairServiceClient
│       ├── config/      AppConfig, FeeProperties, SecurityConfig
│       ├── controller/  InternalRepairQuoteController, PaymentController
│       ├── dto/payment/ PaymentView, SimulatePaymentRequest, SimulatedOutcome
│       ├── dto/quote/   CreateRepairQuoteRequest, QuoteDecisionRequest, RepairQuoteView
│       ├── entity/      BaseEntity, CommissionRecord, OrderItem, PaymentTransaction, ProviderPayout,
│       │                PurchaseOrder, RefundRecord, RepairQuote
│       ├── enums/ (9)  exception/ (5)  health/ (2)  repository/ (7)
│       ├── security/    AuthenticatedAccount, InternalApiKeyGuard, JsonErrorWriter, JwtAuthenticationFilter, JwtVerifier
│       └── service/     FeeCalculator, PaymentService, ReferenceGenerator, RepairQuoteService
│   src/test/java/…      client/, health/ (2), repairquote/ (PaymentApiTest, RepairQuoteApiTest, TestTokens),
│                        repository/PaymentConstraintTest, service/FeeCalculatorTest, app + DB smoke tests → 40 tests
└── repair-service/   (com.reloop.repair, :8083)
    └── src/main/java/com/reloop/repair/
        ├── RepairServiceApplication
        ├── client/      CreateQuoteCommand, HttpOrderServiceClient, OrderServiceClient, QuoteDecisionCommand, QuoteView
        ├── config/      SecurityConfig
        ├── controller/  InternalRepairRequestController, RepairRequestController, RepairerController, RepairerProfileController
        ├── dto/         CreateRepairerProfileRequest, RepairerProfileResponse
        ├── dto/repairer/      16 records (profile, search, nearby, hours, services, reviews, page)
        ├── dto/repairrequest/ 12 records (create, quote, inspection, status, history, payment confirmation)
        ├── entity/      BaseEntity, DeliveryTracking, InventoryMovement, QuoteSnapshot, RepairRequest, RepairService,
        │                RepairStatusHistory, RepairerProfile, RepairerReview, SparePart, WorkingHours
        ├── enums/ (13)  exception/ (12)  health/ (2)  repository/ (8)
        ├── security/    AuthenticatedAccount, InternalApiKeyGuard, JsonErrorWriter, JwtAuthenticationFilter, JwtVerifier
        ├── seed/        DemoRepairerSeeder, DemoRepairers (10 Gujarat workshops)
        └── service/     16 classes (RepairRequestService, RepairTransitions, RepairAction, WorkshopProfileService,
                         RepairerSearchService, GeoUtils, Money, RatingService, …)
    src/main/resources/  application.properties, static/demo-images/repairs/*.svg (5)
    src/test/java/…      client/, health/ (2), profile/, repairer/ (3 + support), repairrequest/ (2 + fixtures),
                         repository/RepairSchemaTest, service/ (5), app + DB smoke tests                   → 95 tests
```

## 2–3. Services, ports and databases

| Service | Port | Package | Spring Boot / Java | Own database (main) | Test database | Schema mode |
|---|---|---|---|---|---|---|
| product-service | **8081** | `com.reloop.product` | 3.5.16 / 21 | H2 file `./data/reloop_product` | H2 mem `product-test-db` | `update` (test: `create-drop`) |
| order-service | **8082** | `com.reloop.orderpayment` | 3.5.16 / 21 | H2 file `./data/reloop_order` | H2 mem `order-test-db` | same |
| repair-service | **8083** | `com.reloop.repair` | 3.5.16 / 21 | H2 file `./data/reloop_repair` | H2 mem `repair-test-db` | same |

✅ Every service has its own datasource, and no service reads another's tables. Cross-service references are plain `Long` IDs with no foreign keys (e.g. `customer_account_id`, `repair_request_id`, `latest_quote_id`). The dependency sets match: web, data-jpa, actuator, security, validation, jjwt 0.12.6, h2.

## 4. Completed features (code + automated tests, CI green)

| Area | What works | Evidence |
|---|---|---|
| Foundation | 3 independent Boot apps, `/api/health`, actuator health, CI matrix | health tests in every service |
| Data model | 25 JPA entities (+2 embeddables, 3 base classes), with constraints (unique keys, CHECK `ck_payments_exactly_one_ref`, `ck_parts_stock_nonnegative`, `ck_reviews_rating`) and indexes | `PaymentConstraintTest`, `RepairSchemaTest`, `AccountRepositoryTest` |
| Customer registration | email/password/name/phone/address/city/pincode; BCrypt (cost 12, 72-byte cap); duplicate email 409 | `AuthApiTest` (20) |
| Repairer registration | account in product-service **plus** workshop profile created in repair-service over REST (`X-Internal-Api-Key`); rolled back if repair-service fails | `AuthApiTest`, `HttpRepairerProfileClientTest` |
| Login / JWT | HS256, issuer `reloop-product-service`, 1 h TTL, secret ≥ 32 bytes, same 401 for an unknown email and a wrong password (timing equalised), suspended/closed accounts get 403 | `JwtServiceTest` (7), `AuthApiTest` |
| Role permissions | CUSTOMER and REPAIRER enforced twice (URL rules + `@PreAuthorize`). repair- and order-service verify the same JWT locally. JSON 401/403 | `RoleBasedAccessTest` (8), access tests in repair/order |
| Marketplace | list/edit/deactivate, browse, keyword search, filters, details, "buy" rules (no buying your own item; only ACTIVE items), product passport + history, demo listings | `ProductApiTest` (20), `ProductBrowseTest` (12), `ProductPurchaseTest` (13) |
| Repairer profiles | create/edit workshop (categories, expertise, priced services, hours, methods, availability), search, nearby/map, 10 demo workshops | `RepairerApiTest` (11), `RepairerSearchApiTest` (14), `DemoRepairersTest` |
| Repair booking workflow | all 12 statuses; accept/reject, inspect, quote, revise, approve/reject quote, progress, complete, cancel; timestamped history; owner-only and assigned-repairer-only access | `RepairRequestFlowTest` (15), `RepairRequestAccessTest` (7), `RepairTransitionsTest` (12 runs) |
| Repair quotes (order-service) | 2% + 2% from configuration, rounded HALF_UP to the paisa, frozen on each quote; revisions supersede older quotes; approve/reject; expiry | `RepairQuoteApiTest` (10), `FeeCalculatorTest` (5) |
| Simulated repair payment | only an APPROVED quote, only by its customer, only once. SUCCESS writes PaymentTransaction + CommissionRecord + ProviderPayout as separate rows. A simulated FAILURE is recorded. The payment rolls back if repair-service refuses | `PaymentApiTest` (11) |

## 5. Incomplete or placeholder features

| Feature | State |
|---|---|
| **Marketplace orders & payments** | `POST /api/products/{id}/purchase` marks the product SOLD and transfers the passport **immediately**. It doesn't create a `PurchaseOrder`, there's no simulated payment, and **no 2% + 2% is charged**. The response says this is deferred. order-service has `PurchaseOrder`/`OrderItem` entities but **no service or API** |
| Payment cancellation outcome | `PaymentStatus.CANCELLED` exists but can't be produced (only SUCCESS and FAILURE are simulated) |
| Refunds | `RefundRecord` entity + repository only |
| Earnings / payouts views | Payout rows are written but there's no endpoint. `ProviderPayout.providerAccountId` is **null** for repair payouts (only the repair-service profile id is known) |
| Customer / repairer dashboards | `/api/customer/dashboard` and `/api/repairer/dashboard` return a static welcome map (role-check demo only) |
| Reviews | demo reviews are seeded and shown; there's **no endpoint to write a review** |
| Spare parts / inventory | `SparePart`, `InventoryMovement` entities + repositories, no service or API |
| Delivery tracking | `DeliveryTracking` entity + repository, no service or API |
| Product images | URL strings only, bundled SVG placeholders; no upload or hosting (as instructed) |
| Admin (verify/suspend repairers, moderation) | none |
| Frontend, Docker, cloud deployment, OpenAPI docs | none |

## 6. REST contracts between services

| Direction | Endpoint | Sender DTO → receiver DTO | Field-by-field | Auth | Tested |
|---|---|---|---|---|---|
| product → repair | `POST /api/repairer-profiles` | `CreateRepairerProfileCommand` → `CreateRepairerProfileRequest` | ✅ 14/14 names match (`boolean demo` → `Boolean demo` OK) | key, constant-time | both sides, separately |
| product → repair | `GET /api/repairer-profiles/{id}` | → `RepairerProfileResponse` → `RepairerProfileView` | ✅ 16/16 (enums read as strings) | **none** (see C-6) | both sides |
| repair → order | `POST /api/internal/repair-quotes` | `CreateQuoteCommand` → `CreateRepairQuoteRequest` | ✅ 6/6 | key | both sides |
| repair → order | `POST /api/internal/repair-quotes/{id}/approve\|reject` | `QuoteDecisionCommand` → `QuoteDecisionRequest` | ✅ 3/3 | key | both sides |
| order → repair (response) | same | `RepairQuoteView` → `QuoteView` | ✅ the 16 consumed fields match; 3 extra fields are ignored (Jackson default in `RestClient`) | — | both sides |
| order → repair | `POST /api/internal/repair-requests/{id}/payment-confirmation` | `PaymentConfirmation` → `PaymentConfirmationRequest` | ✅ 4/4 | key | both sides |
| browser → order/repair | Bearer JWT from product-service | `sub`, `email`, `role`, `iss` | ✅ the same verifier in both (the files differ only in a comment) | HS256 shared secret | yes |

There is no broken contract. However, each side is tested only against **its own mock** of the other side. No test runs two real services together, so a future rename on one side would not fail CI (see R-5).

## 7. Build and test results (actually run)

GitHub Actions run **45712350871** (workflow_dispatch, commit 540425d). Each job took about 30 s and passed every step ("Build and run tests" = `mvn verify`).

| Service | `mvn verify` | Tests run | Failures | Errors | Skipped |
|---|---|---|---|---|---|
| product-service | ✅ success | **113** | 0 | 0 | 0 |
| order-service | ✅ success | **40** | 0 | 0 | 0 |
| repair-service | ✅ success | **95** | 0 | 0 | 0 |
| **Total** | | **248** | 0 | 0 | 0 |

The totals match the test methods in the source: 113, 40, and 93 + one 3-case parameterized test = 95. The same totals were reported on the two earlier push runs (45699987565, 45700414647).

- **Compilation errors:** none (compile, test-compile and package all ran inside `verify`).
- **Missing dependencies:** none found. The POMs resolve, and every module needed by the code is declared.

## 8. Critical issues and inconsistencies

No compile errors, failing tests or broken contracts were found. The issues are design and readiness gaps, ordered by impact on the next phases.

| # | Severity | Issue | Where |
|---|---|---|---|
| C-1 | **High (requirement gap)** | Marketplace purchases bypass order-service: no order, no simulated payment, **no 2% customer fee or 2% seller commission**. Only repairs follow the 2% + 2% model | `ProductService.purchase` |
| C-2 | **High (demo blocker)** | No complete, bookable workshop is owned by a loginable repairer. The 10 seeded demo workshops have placeholder (negative) account IDs, so nobody can accept their requests. The seeded demo repairer's own profile starts **incomplete** (not listed or bookable) until filled in via `PUT /api/repairers/{id}`. A fresh demo of the repair flow therefore needs manual steps | `DemoRepairers`, `DemoAccountSeeder`, `RepairerProfileService.create` |
| C-3 | **High (next-phase blocker)** | No CORS configuration in any service. A browser frontend on another origin (e.g. Vite on :5173) will be blocked | all `SecurityConfig` |
| C-4 | Medium | Payment dual write: order-service calls repair-service **inside** its DB transaction. If repair-service commits PAID and order-service's commit then fails, repair shows PAID with no payment row. Unlikely, but there is no reconciliation or outbox | `PaymentService.simulateRepairPayment` |
| C-5 | Medium (deploy) | Unsafe defaults: the dev JWT secret and internal key are used when the env vars are missing (only a warning is logged); H2 console is enabled and public (`sa`, no password); actuator `show-details=always` is public | all `application.properties`, `SecurityConfig` |
| C-6 | Low–Medium | `GET /api/repairer-profiles/{id}` is unauthenticated (the internal key is checked only on POST). It exposes `accountExternalId` and unlisted or incomplete profiles | `RepairerProfileController.get` |
| C-7 | Low | Inconsistent configuration: product-service's `reloop.services.repair.base-url` and `reloop.seed.enabled` have no env-var override (the other services do), which will bite in Docker | product `application.properties` |
| C-8 | Low | Inconsistent error JSON: product-service handles 404/405/415/500 itself; order- and repair-service fall back to Spring's default `/error` body for these | `ApiExceptionHandler` (order, repair) |
| C-9 | Low | Overloaded ID meaning: `PaymentTransaction.providerId` / `ProviderPayout.providerId` hold a repair-service **profile id** for repairs but are intended as an account id for sellers; `providerAccountId` is null for repairs | order-service entities |
| C-10 | Low | No schema migrations (`ddl-auto=update` on file DBs). Old local `./data/*.mv.db` files from earlier phases may not upgrade cleanly | all services |
| C-11 | Low (maintenance) | Security classes are copied into 3 services (JWT filter, verifier, error writer, key guard). repair-service also has a second key check in `RepairerProfileController` | `security/` packages |
| C-12 | Low (docs) | Stale docs: ARCHITECTURE §8 still says "Foundation ← you are here"; PHASE1.md describes the pre-security state | `docs/` |
| C-13 | Info | JWTs can't be revoked: a suspended account's token stays valid in order- and repair-service for up to 1 h | by design (stateless) |

## 9. Compliance with the master requirements

| Requirement | Status |
|---|---|
| 3 Spring Boot microservices, database per service, REST only | ✅ |
| Two account types, separate registration, role-based access | ✅ (no account conversion path exists) |
| Repair workflow REQUESTED → … → COMPLETED, approval required before payment | ✅ 12 statuses; enforced in both services |
| Rejected quotes never paid; revised quotes need fresh approval | ✅ enforced in both services, tested |
| Work only after payment | ✅ `IN_PROGRESS` only from `PAID` |
| Status history with timestamps | ✅ `repair_status_history`, actor + note |
| Simulated payments, no card/CVV/UPI PIN data | ✅ no such fields or columns (PaymentTransaction has none) |
| Success / failure / **cancellation** outcomes | ⚠️ success and failure only |
| 2% customer fee + 2% provider commission, configurable, frozen per transaction | ✅ **for repairs**. ❌ **not applied to marketplace sales** (C-1) |
| Integer paise / BigDecimal money | ✅ everywhere (`*_paise BIGINT`, rates `DECIMAL(8,6)`) |
| Commission and payout as separate records | ✅ for repairs |
| Earnings from commission/payout records | ❌ no earnings API yet |
| Refunds reverse ledger entries | ❌ not implemented |
| 30+ test cases | ✅ 248 automated tests |

## 10. Recommended fixes before continuing (in order)

None of these needs an architectural change.

1. **R-1 Demo readiness (C-2):** seed the demo repairer's profile as *complete* (categories, one service, hours), or link one seeded workshop to the demo repairer account. A fresh start can then demo booking → quote → pay → complete.
2. **R-2 CORS (C-3):** add a shared, configurable allowed-origins property (`reloop.cors.allowed-origins`) to the three `SecurityConfig`s before frontend work starts.
3. **R-3 Marketplace orders (C-1):** next phase. product-service `purchase` should call order-service (create `PurchaseOrder` → simulate payment with the same `FeeCalculator` → commission + seller payout) and mark SOLD only after payment confirmation, reusing the repair payment pattern.
4. **R-4 Safer defaults (C-5, C-6, C-7):** fail startup when the dev secret or key is used under a non-dev profile; disable the H2 console outside the dev profile; set `show-details=when-authorized`; require the internal key on `GET /api/repairer-profiles/{id}`; add env-var overrides in product-service.
5. **R-5 Cross-service smoke test:** a CI job that starts the three jars and runs the REPAIR_WORKFLOW curl walkthrough end to end. This is the only check that would catch a contract drift between services.
6. **R-6 Payment robustness (C-4):** record the payment as PENDING, commit, call repair-service, then mark SUCCESS (or add a small retry/reconciliation job). Add the CANCELLED simulated outcome.
7. **R-7 Payout identity (C-9):** pass the repairer's account id in `CreateQuoteCommand` and store it on the quote and payout, so the earnings phase can query by account.
8. **R-8 Housekeeping (C-8, C-10, C-12):** add 404/405/500 handlers to order- and repair-service; plan Flyway before PostgreSQL; refresh ARCHITECTURE §8 and PHASE1.md.
