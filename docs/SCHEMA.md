# ReLoop — Initial Schema Configuration

## How the schema is created

Each service generates **its own** schema from its JPA entities at startup —
there is no shared database and no cross-service foreign key.

| Environment | Property | Effect |
|---|---|---|
| Runtime (H2 file) | `spring.jpa.hibernate.ddl-auto=update` | Creates/alters tables in `data/reloop_<svc>` on startup, keeps existing rows |
| Tests (H2 in-memory) | `spring.jpa.hibernate.ddl-auto=create-drop` | Fresh schema per test JVM, dropped on exit |
| PostgreSQL (later) | switch `spring.datasource.*` + keep `update` (or export DDL once via `ddl-auto=validate`) | Same entities, no code change |

Config locations: `product-service/src/main/resources/application.properties`,
`order-service/...`, `repair-service/...` (and the `src/test/resources/` twins).

Naming: Spring Boot default `CamelCaseToSnakeCasePhysicalNamingStrategy` —
Java `pricePaise` → column `price_paise`.

## Physical model summary

### product-service (`reloop_product`)

| Table | Key constraints | Indexes |
|---|---|---|
| `accounts` | **UNIQUE** `email`; `role`/`status` NOT NULL | `ix_accounts_role_status(role, status)` |
| `customer_accounts` | **UNIQUE** `account_id`; FK → `accounts` | `ix_customer_accounts_name(last_name, first_name)` |
| `repairer_account_identities` | **UNIQUE** `account_id`, **UNIQUE** `repairer_profile_id`; FK → `accounts` | `ix_rai_verification(verification_status)` |
| `cities` | `name`/`state` NOT NULL | `ix_cities_name`, `ix_cities_state` |
| `addresses` | FK → `accounts`, `cities`; `status` NOT NULL | `ix_addresses_account`, `ix_addresses_city`, `ix_addresses_pincode` |
| `stores` | FK → `accounts`, `cities`; `status` NOT NULL | `ix_stores_owner`, `ix_stores_city`, `ix_stores_name` |
| `products` | FK → `accounts` (seller, buyer), `stores`, `product_passports`; `price_paise`, `item_condition`, `passport_id` NOT NULL | `ix_products_seller_status`, `ix_products_title`, `ix_products_category`, `ix_products_store`, `ix_products_status_created(status, created_at)`, `ix_products_city`, `ix_products_price`, `ix_products_passport` |
| `product_passports` | **UNIQUE** `passport_code` (`uk_passports_code`, immutable); FK → `accounts` (current owner) | `ix_passports_owner` |
| `product_history_events` | FK → `product_passports`, `products`, `accounts` (actor); every column `updatable = false` (append-only) | `ix_history_passport_created(passport_id, created_at)`, `ix_history_product` |
| `product_images` | FK → `products` (cascade delete via orphanRemoval) | `ix_product_images_product(product_id, sort_order)` |

### order-service (`reloop_order`)

| Table | Key constraints | Indexes |
|---|---|---|
| `purchase_orders` | **UNIQUE** `order_number`; all money NOT NULL; both fee rates, the seller net and ReLoop revenue frozen at creation; `expires_at` payment window | `ix_orders_buyer`, `ix_orders_seller`, `ix_orders_status`, `ix_orders_product`, `ix_orders_expires` |
| `order_items` | FK → `purchase_orders`; `product_external_id` is a plain Long (**no FK**) | `ix_order_items_order`, `ix_order_items_product_external` |
| `repair_quotes` | **UNIQUE** `(repair_request_id, quote_revision)`; both fee rates + amounts NOT NULL; status `SENT → APPROVED/REJECTED`, older revisions `SUPERSEDED`; `valid_until` (`reloop.quotes.valid-days`) | `ix_quotes_request`, `ix_quotes_status`, `ix_quotes_repairer` |
| `payment_transactions` | **UNIQUE** `transaction_id` ⭐; **UNIQUE** `settlement_key` (set only on SUCCESS: `ORDER-{id}` / `QUOTE-{id}`, so an order or quote can be paid once); **UNIQUE** `(buyer_account_id, idempotency_key)`; **CHECK** `ck_payments_exactly_one_ref` (exactly one of order/quote); `transaction_type`, `provider_account_id`, `platform_revenue_paise`, `refunded_amount_paise`. Provider commission is stored in the `repairer_commission_*` columns (names kept). **No PAN/CVV/UPI-PIN/bank columns exist** | `ix_payments_status`, `ix_payments_order`, `ix_payments_quote`, `ix_payments_buyer`, `ix_payments_provider_account`, `ix_payments_type`, `ix_payments_created` |
| `commission_records` | **UNIQUE** `payment_transaction_id`, **UNIQUE** `transaction_id` (`COM-…`); frozen rates; customer fee and provider commission kept separately, plus reversed amounts | `ix_commission_status`, `ix_commission_type` |
| `provider_payouts` | **UNIQUE** `payout_reference`, **UNIQUE** `payment_transaction_id`; `net = gross - commission`; refunded gross, reversed commission and reversed net tracked | `ix_payouts_provider`, `ix_payouts_provider_account`, `ix_payouts_status` |
| `refund_records` | **UNIQUE** `refund_reference`; **CHECK** `ck_refunds_amounts`; base, fee, commission and provider-net shares of each refund | `ix_refunds_payment`, `ix_refunds_status` |
| `ledger_entries` | **CHECK** `ck_ledger_positive` (amount > 0); append-only double entry: debits = credits per payment; reversal lines reference the line they reverse | `ix_ledger_payment`, `ix_ledger_account_party`, `ix_ledger_refund` |
| `fee_rate_configs` | **UNIQUE** `transaction_type`; **CHECK** `ck_fee_rates_range` (0–0.5). Optional override of the application.properties rates | — |

### repair-service (`reloop_repair`)

| Table | Key constraints | Indexes |
|---|---|---|
| `repairer_profiles` | **UNIQUE** `account_external_id` (external ID, no FK; negative = demo placeholder); search summary columns `starting_price_paise`, `rating_average`, `rating_count`, `profile_complete`, `availability`, `is_demo` | `ix_profiles_status`, `ix_profiles_city`, `ix_profiles_business_name`, `ix_profiles_listing(status, profile_complete)`, `ix_profiles_lat_lng(latitude, longitude)` |
| `repairer_profile_categories` | FK → `repairer_profiles`; enum `category` | `ix_profile_categories_category` |
| `repairer_working_hours` | FK → `repairer_profiles`; `day_of_week`, `opens_at`, `closes_at` | — |
| `repairer_reviews` | FK → `repairer_profiles`; **CHECK** `ck_reviews_rating (rating BETWEEN 1 AND 5)`; `is_demo` | `ix_reviews_profile_created(repairer_profile_id, created_at)` |
| `repair_services` | FK → `repairer_profiles`; estimate in paise; `price_type` FIXED/STARTING_FROM; removed services kept INACTIVE | `ix_services_profile(repairer_profile_id, status)`, `ix_services_category` |
| `repair_requests` | **UNIQUE** `request_number` (`RR-yyyyMMdd-XXXXXXXX`); FK → `repairer_profiles`; customer/quote/payment IDs external (`latest_quote_id`, `payment_transaction_id`); `problem_description`, `inspection_notes`, `close_reason`; read-only quote snapshot `quote_*` columns (revision, base/fee/total/commission/net paise, summary, estimated days, sent at) copied from order-service; per-step timestamps (`accepted_at` … `closed_at`); optimistic lock `version` | `ix_requests_customer`, `ix_requests_profile_status`, `ix_requests_status` |
| `repair_request_images` | FK → `repair_requests`; ≤ 5 per request, `/demo-images/...` or `https://` URLs (no hosting yet) | — |
| `repair_status_history` | FK → `repair_requests`; append-only; `from_status`, `to_status`, actor `CUSTOMER`/`REPAIRER`/`SYSTEM`, account id, note, `created_at` — one row per status change (see [REPAIR_WORKFLOW.md](REPAIR_WORKFLOW.md)) | `ix_history_request_time(repair_request_id, created_at)` |
| `spare_parts` | **UNIQUE** `(repairer_profile_id, sku)`; **CHECK** `ck_parts_stock_nonnegative (stock_quantity >= 0 AND reorder_level >= 0 AND unit_cost_paise >= 0)` | `ix_parts_profile_status`, `ix_parts_profile_category`, `ix_parts_name` |
| `inventory_movements` | FK → `repairer_profiles`, `spare_parts`, `repair_requests`; signed `quantity_change`; **CHECK** `ck_movements_valid (quantity_change <> 0 AND stock_after >= 0 AND unit_cost_paise >= 0)`; types STOCK_IN / USED_IN_REPAIR / ADJUSTMENT (see [INVENTORY.md](INVENTORY.md)) | `ix_movements_part_time`, `ix_movements_profile_date`, `ix_movements_request`, `ix_movements_type` |
| `delivery_tracking` | FK → `repair_requests`; map coordinates | `ix_tracking_request(repair_request_id, status)`, `ix_tracking_number` |

## Money & rate conventions

- **Amounts**: `*_paise BIGINT` — ₹1 = 100 paise. Never `double`/`float`.
- **Rates**: `DECIMAL(8,6)` — `0.0200` = 2%. Copied onto every order, quote,
  transaction and commission row at creation time, **never re-read from
  config later**. Source: `fee_rate_configs` if a row exists for the transaction
  type, otherwise `reloop.fees.*` in application.properties.
- **Example** (blueprint): repair ₹1,000 → `base=100000`, customer fee 2% =
  `2000`, customer pays `102000`; repairer commission 2% = `2000`, repairer
  net `98000`; ReLoop total `4000` paise (₹40).

## Verification

Constraint coverage is proven by tests (not just asserted):

- `AccountRepositoryTest` — unique email, timestamps, paise price
- `PaymentConstraintTest` — unique transaction ID, exactly-one-reference
  CHECK, unique order number, frozen-rate commission ledger
- `RepairSchemaTest` — unique profile per account, unique SKU per repairer,
  `stock >= 0` CHECK, status-history queries

```bash
mvn -f product-service/pom.xml test
mvn -f order-service/pom.xml test
mvn -f repair-service/pom.xml test
```

Inspect a live schema: start a service, open `http://localhost:808X/h2-console`
(JDBC URL `jdbc:h2:file:./data/reloop_<svc>`, user `sa`, empty password) and run
`SCRIPT NODATA AS SELECT` or browse the table list.

> Phase 1 rule reminder: `data/` directories are git-ignored; deleting them
> simply regenerates the schema on next startup (`ddl-auto=update`).
