# ReLoop — ER Diagrams (Mermaid)

Authoritative data-model diagrams for the three microservices. Each service owns
its own database — there are **no cross-database foreign keys**; cross-service
links are plain `Long` external IDs.

## 1) product-service — DB `reloop_product`

```mermaid
erDiagram
    ACCOUNT ||--o| CUSTOMER_ACCOUNT : "role=CUSTOMER"
    ACCOUNT ||--o| REPAIRER_ACCOUNT_IDENTITY : "role=REPAIRER"
    ACCOUNT ||--o{ ADDRESS : owns
    ACCOUNT ||--o{ STORE : owns
    ACCOUNT ||--o{ PRODUCT : sells
    CITY ||--o{ ADDRESS : located_in
    CITY ||--o{ STORE : located_in
    STORE ||--o{ PRODUCT : lists
    PRODUCT ||--o{ PRODUCT_IMAGE : has
    ACCOUNT ||--o{ PRODUCT : buys
    PRODUCT_PASSPORT ||--o{ PRODUCT : "one item, 1..n listings"
    ACCOUNT ||--o{ PRODUCT_PASSPORT : "currently owns"
    PRODUCT_PASSPORT ||--o{ PRODUCT_HISTORY_EVENT : "timeline (append-only)"
    PRODUCT ||--o{ PRODUCT_HISTORY_EVENT : "event of"

    ACCOUNT {
        bigint id PK
        string email UK "login, unique"
        string password_hash "never plain text"
        string role "enum CUSTOMER|REPAIRER - immutable"
        string status "enum ACTIVE|SUSPENDED|CLOSED"
        timestamp created_at
        timestamp updated_at
    }
    CUSTOMER_ACCOUNT {
        bigint id PK
        bigint account_id FK "UK -> accounts.id"
        string first_name
        string last_name
        timestamp created_at
        timestamp updated_at
    }
    REPAIRER_ACCOUNT_IDENTITY {
        bigint id PK
        bigint account_id FK "UK -> accounts.id"
        bigint repairer_profile_id "ext repair-service id - no FK"
        string verification_status "enum UNVERIFIED|VERIFIED|REJECTED"
        timestamp verified_at
        timestamp created_at
        timestamp updated_at
    }
    CITY {
        bigint id PK
        string name "indexed"
        string state "indexed"
        string country
        double latitude
        double longitude
        timestamp created_at
        timestamp updated_at
    }
    ADDRESS {
        bigint id PK
        bigint account_id FK "indexed"
        bigint city_id FK "indexed"
        string line1
        string line2
        string pincode "indexed"
        boolean is_default
        string status "enum ACTIVE|ARCHIVED"
        timestamp created_at
        timestamp updated_at
    }
    STORE {
        bigint id PK
        bigint owner_account_id FK "indexed"
        bigint city_id FK "indexed"
        string name "indexed"
        string status "enum ACTIVE|CLOSED"
        timestamp created_at
        timestamp updated_at
    }
    PRODUCT {
        bigint id PK
        bigint seller_account_id FK "indexed"
        bigint store_id FK "nullable, indexed"
        string title "indexed - browse search"
        string description
        string category "enum ProductCategory - indexed"
        bigint price_paise "integer paise"
        string status "enum DRAFT|ACTIVE|RESERVED|SOLD|ARCHIVED - availability"
        string item_condition "enum LIKE_NEW|VERY_GOOD|GOOD|FAIR|NEEDS_REPAIR"
        string location_city "indexed"
        string pickup_location "nullable"
        bigint passport_id FK "NOT NULL, indexed"
        bigint buyer_account_id FK "nullable - set when SOLD"
        timestamp sold_at
        timestamp created_at
        timestamp updated_at
    }
    PRODUCT_PASSPORT {
        bigint id PK
        string passport_code UK "uk_passports_code - RLP-XXXX-XXXX-XXXX, immutable"
        bigint current_owner_account_id FK "indexed - moves to buyer on purchase"
        timestamp created_at
        timestamp updated_at
    }
    PRODUCT_HISTORY_EVENT {
        bigint id PK
        bigint passport_id FK "indexed with created_at"
        bigint product_id FK "indexed"
        string event_type "LISTED|RELISTED|UPDATED|PRICE_CHANGED|DEACTIVATED|REACTIVATED|SOLD"
        bigint actor_account_id FK "nullable"
        bigint price_paise "price at the time of the event"
        string details
        timestamp created_at
        timestamp updated_at
    }
    PRODUCT_IMAGE {
        bigint id PK
        bigint product_id FK "indexed with sort_order"
        string url
        int sort_order
        boolean is_primary
        string status "enum ACTIVE|HIDDEN"
        timestamp created_at
        timestamp updated_at
    }
```

## 2) order-service — DB `reloop_order`

All money columns are `bigint` paise and all rates are `decimal(8,6)`. Each order, quote, payment, commission and
payout row keeps the rates that were in force when it was created (see docs/PAYMENTS.md).

```mermaid
erDiagram
    PURCHASE_ORDER ||--o{ ORDER_ITEM : contains
    PURCHASE_ORDER ||--o{ PAYMENT_TRANSACTION : "attempt(s)"
    REPAIR_QUOTE ||--o{ PAYMENT_TRANSACTION : "attempt(s)"
    PAYMENT_TRANSACTION ||--o| COMMISSION_RECORD : "on SUCCESS"
    PAYMENT_TRANSACTION ||--o| PROVIDER_PAYOUT : "on SUCCESS"
    PAYMENT_TRANSACTION ||--o{ REFUND_RECORD : "0..n refunds"
    PAYMENT_TRANSACTION ||--o{ LEDGER_ENTRY : "4 lines on SUCCESS"
    REFUND_RECORD ||--o{ LEDGER_ENTRY : "4 reversal lines"
    LEDGER_ENTRY ||--o| LEDGER_ENTRY : "reversal_of"

    PURCHASE_ORDER {
        bigint id PK
        string order_number UK "ORD-yyyyMMdd-XXXXXXXXXX"
        bigint buyer_account_id "ext product-service - indexed"
        bigint seller_account_id "ext product-service - indexed"
        bigint product_external_id "ext product-service - indexed"
        string status "PENDING_PAYMENT|CONFIRMED|CANCELLED|PARTIALLY_REFUNDED|REFUNDED"
        bigint subtotal_paise
        decimal customer_fee_rate "frozen"
        bigint customer_fee_paise
        bigint total_paise "subtotal + customer fee"
        decimal provider_commission_rate "frozen"
        bigint provider_commission_paise
        bigint seller_net_paise "subtotal - commission"
        bigint platform_revenue_paise "fee + commission"
        bigint refunded_amount_paise
        string currency
        timestamp expires_at "payment window - indexed"
        timestamp paid_at
        timestamp cancelled_at
        string cancel_reason
        boolean product_released "reservation released in product-service"
        string failure_reason "last failed attempt"
        timestamp refunded_at
    }
    ORDER_ITEM {
        bigint id PK
        bigint order_id FK "indexed"
        bigint product_external_id "ext product-service - indexed"
        string product_title_snapshot
        bigint unit_price_paise
        int quantity
        bigint line_total_paise
    }
    REPAIR_QUOTE {
        bigint id PK
        bigint repair_request_id "ext repair-service - UK with quote_revision"
        bigint repairer_profile_id "ext repair-service - indexed"
        bigint repairer_account_id "ext product-service - indexed (payouts)"
        bigint customer_account_id "ext product-service"
        int quote_revision "1,2,3..."
        string status "DRAFT|SENT|APPROVED|REJECTED|SUPERSEDED"
        string summary
        bigint base_amount_paise
        decimal customer_fee_rate "frozen"
        bigint customer_fee_paise
        bigint total_payable_paise "base + customer fee"
        decimal repairer_commission_rate "frozen"
        bigint repairer_commission_paise
        bigint repairer_net_paise "base - commission"
        timestamp sent_at
        timestamp approved_at
        timestamp rejected_at
        timestamp valid_until
    }
    PAYMENT_TRANSACTION {
        bigint id PK
        string transaction_id UK "TXN-... never duplicated"
        string transaction_type "MARKETPLACE_PURCHASE|REPAIR_BOOKING - indexed"
        bigint purchase_order_id "exactly one ref (CHECK)"
        bigint repair_quote_id "exactly one ref (CHECK)"
        bigint buyer_account_id "indexed"
        bigint provider_id "seller account or repairer profile"
        bigint provider_account_id "seller/repairer account - indexed"
        string provider_type "SELLER|REPAIRER"
        string method "UPI|CARD|NET_BANKING|WALLET"
        string status "SUCCESS|FAILED|CANCELLED|PARTIALLY_REFUNDED|REFUNDED"
        bigint base_amount_paise
        decimal customer_fee_rate "rate at transaction time"
        bigint customer_fee_paise
        bigint total_charged_paise "base + customer fee"
        decimal repairer_commission_rate "provider commission rate (column name kept)"
        bigint repairer_commission_paise "provider commission"
        bigint provider_net_paise "base - commission"
        bigint platform_revenue_paise "fee + commission"
        bigint refunded_amount_paise
        string settlement_key UK "ORDER-id|QUOTE-id, SUCCESS only"
        string idempotency_key "UK with buyer_account_id"
        string failure_reason
        timestamp attempted_at
        timestamp completed_at
    }
    COMMISSION_RECORD {
        bigint id PK
        bigint payment_transaction_id UK "one per txn"
        string transaction_id UK "COM-..."
        string transaction_type
        string reference_type "PURCHASE_ORDER|REPAIR_QUOTE"
        bigint reference_id
        decimal customer_fee_rate "frozen"
        bigint customer_fee_paise
        decimal provider_commission_rate "frozen"
        bigint provider_commission_paise
        bigint total_commission_paise "ReLoop revenue"
        bigint reversed_customer_fee_paise
        bigint reversed_provider_commission_paise
        string status "RECORDED|PARTIALLY_REVERSED|REVERSED"
    }
    PROVIDER_PAYOUT {
        bigint id PK
        string payout_reference UK "PO-..."
        bigint payment_transaction_id UK "one per txn"
        bigint provider_id "indexed"
        string provider_type "SELLER|REPAIRER"
        bigint provider_account_id "indexed - dashboards"
        string transaction_type
        bigint gross_amount_paise
        bigint commission_paise
        bigint net_amount_paise "gross - commission"
        bigint refunded_gross_paise
        bigint reversed_commission_paise
        bigint reversed_net_paise
        string status "PENDING|PAID|ON_HOLD|PARTIALLY_REVERSED|REVERSED"
        timestamp paid_at
    }
    REFUND_RECORD {
        bigint id PK
        string refund_reference UK "RFD-..."
        bigint payment_transaction_id "indexed"
        string transaction_type
        bigint order_id
        bigint refund_amount_paise "to the customer (base + fee share)"
        bigint refunded_base_paise
        bigint refunded_customer_fee_paise
        bigint reversed_provider_commission_paise
        bigint provider_net_reversed_paise
        bigint reversed_commission_paise "ReLoop revenue reversed"
        string status "INITIATED|COMPLETED|FAILED"
        string reason "CUSTOMER_REQUEST|ITEM_NOT_AS_DESCRIBED|REPAIR_NOT_COMPLETED|DISPUTED_ORDER|ORDER_CANCELLED|OTHER"
        string note
        bigint initiated_by_account_id "null = ReLoop support"
        timestamp completed_at
    }
    LEDGER_ENTRY {
        bigint id PK
        bigint payment_transaction_id "indexed"
        string transaction_reference "TXN-..."
        bigint refund_record_id "indexed - reversal lines"
        string transaction_type
        string entry_type "PAYMENT|REFUND"
        string account "CUSTOMER_PAYMENTS|PROVIDER_PAYABLE|PLATFORM_FEE_REVENUE|PLATFORM_COMMISSION_REVENUE"
        string direction "DEBIT|CREDIT"
        bigint amount_paise "CHECK > 0"
        bigint party_account_id "buyer or provider"
        bigint reversal_of_entry_id "line being reversed"
        string description
    }
    FEE_RATE_CONFIG {
        bigint id PK
        string transaction_type UK
        decimal customer_fee_rate "CHECK 0..0.5"
        decimal provider_commission_rate "CHECK 0..0.5"
        string note
    }
```

## 3) repair-service — DB `reloop_repair`

```mermaid
erDiagram
    REPAIRER_PROFILE ||--o{ REPAIR_SERVICE : offers
    REPAIRER_PROFILE ||--o{ REPAIRER_PROFILE_CATEGORY : "repair categories"
    REPAIRER_PROFILE ||--o{ REPAIRER_WORKING_HOURS : "opening hours"
    REPAIRER_PROFILE ||--o{ REPAIRER_REVIEW : rated
    REPAIRER_PROFILE ||--o{ REPAIR_REQUEST : receives
    REPAIRER_PROFILE ||--o{ SPARE_PART : stocks
    REPAIR_SERVICE ||--o{ REPAIR_REQUEST : "optional choice"
    REPAIR_REQUEST ||--o{ REPAIR_STATUS_HISTORY : timeline
    REPAIR_REQUEST ||--o{ REPAIR_REQUEST_IMAGE : photos
    REPAIR_REQUEST ||--o{ INVENTORY_MOVEMENT : "parts used"
    REPAIR_REQUEST |o--o{ DELIVERY : "repair legs (simulated)"
    DELIVERY ||--o{ DELIVERY_LOCATION_POINT : "coordinate history"
    SPARE_PART ||--o{ INVENTORY_MOVEMENT : movements
    REPAIRER_PROFILE ||--o{ INVENTORY_MOVEMENT : owns

    REPAIRER_PROFILE {
        bigint id PK
        bigint account_external_id UK "ext product-service - no FK"
        string business_name "indexed - search"
        string description
        string specialties
        string phone
        string address_line "workshop location"
        string city "indexed"
        string pincode
        double latitude "Leaflet/OSM"
        double longitude "Leaflet/OSM"
        string service_hours
        boolean accepts_in_person "delivery mode"
        boolean accepts_pickup_drop "delivery mode"
        boolean accepts_shipping "delivery mode"
        string verification_status "enum UNVERIFIED|VERIFIED|REJECTED"
        string status "enum ACTIVE|INACTIVE|SUSPENDED - indexed"
        string availability "enum AVAILABLE|BUSY|UNAVAILABLE"
        string availability_note
        bigint starting_price_paise "min active service price - sort"
        double rating_average "summary of reviews"
        int rating_count
        boolean profile_complete "listed in search when ACTIVE and complete"
        boolean is_demo "seeded demo data"
        timestamp created_at
        timestamp updated_at
    }
    REPAIRER_PROFILE_CATEGORY {
        bigint repairer_profile_id FK
        string category "enum RepairServiceCategory - indexed"
    }
    REPAIRER_WORKING_HOURS {
        bigint repairer_profile_id FK
        string day_of_week "MONDAY..SUNDAY"
        time opens_at
        time closes_at
    }
    REPAIRER_REVIEW {
        bigint id PK
        bigint repairer_profile_id FK "indexed with created_at"
        int rating "CHECK 1-5"
        string comment
        string reviewer_name
        bigint customer_account_external_id "ext - nullable"
        boolean is_demo
        timestamp created_at
        timestamp updated_at
    }
    REPAIR_SERVICE {
        bigint id PK
        bigint repairer_profile_id FK "indexed with status"
        string name
        string description
        string category "enum RepairServiceCategory - indexed"
        bigint estimated_price_paise "nullable"
        string price_type "enum FIXED|STARTING_FROM"
        int estimated_days
        string status "enum ACTIVE|INACTIVE"
        timestamp created_at
        timestamp updated_at
    }
    REPAIR_REQUEST {
        bigint id PK
        string request_number UK "uk_requests_number"
        bigint customer_account_id "ext product-service - indexed"
        bigint repairer_profile_id FK "indexed with status"
        bigint repair_service_id FK "nullable"
        bigint delivery_address_id "ext product-service - no FK"
        string delivery_mode "enum IN_PERSON|PICKUP_DROP|SHIPPING"
        string item_category "enum RepairServiceCategory"
        string item_description
        string problem_description
        string customer_notes
        string inspection_notes
        string close_reason "reject or cancel reason"
        string status "12 RepairStatus values - indexed"
        bigint latest_quote_id "ext order-service - no FK"
        int quote_revision "snapshot of current quote"
        bigint quote_base_amount_paise "snapshot"
        bigint quote_customer_fee_paise "snapshot"
        bigint quote_total_payable_paise "snapshot"
        bigint quote_repairer_commission_paise "snapshot"
        bigint quote_repairer_net_paise "snapshot"
        string quote_summary "snapshot"
        int quote_estimated_days "snapshot"
        timestamp quote_sent_at "snapshot"
        bigint payment_transaction_id "ext order-service - no FK"
        bigint version "optimistic lock"
        timestamp accepted_at
        timestamp inspected_at
        timestamp quoted_at
        timestamp approved_at
        timestamp paid_at
        timestamp in_progress_at
        timestamp completed_at
        timestamp closed_at
        timestamp created_at
        timestamp updated_at
    }
    REPAIR_REQUEST_IMAGE {
        bigint repair_request_id FK
        string image_url "demo-images path or https"
        int position "display order"
    }
    REPAIR_STATUS_HISTORY {
        bigint id PK
        bigint repair_request_id FK "indexed with created_at"
        string from_status "nullable on first entry"
        string to_status "enum RepairStatus"
        string changed_by "enum CUSTOMER|REPAIRER|SYSTEM"
        bigint changed_by_account_id "ext product-service"
        string note
        timestamp created_at
        timestamp updated_at
    }
    SPARE_PART {
        bigint id PK
        bigint repairer_profile_id FK "API repairerId; UK with sku"
        string sku "unique per repairer, generated if omitted"
        string name "API partName, indexed"
        string category "enum SparePartCategory"
        string description
        bigint unit_cost_paise "API unitCost, integer paise, CHECK >= 0"
        int stock_quantity "API quantity, CHECK >= 0"
        int reorder_level "API lowStockThreshold, CHECK >= 0"
        string status "enum ACTIVE|DISCONTINUED"
        timestamp created_at
        timestamp updated_at
    }
    INVENTORY_MOVEMENT {
        bigint id PK
        bigint repairer_profile_id FK "API repairerId, indexed with movement_date"
        bigint spare_part_id FK "indexed with created_at"
        bigint repair_request_id FK "nullable, required for USED_IN_REPAIR"
        string movement_type "STOCK_IN|USED_IN_REPAIR|ADJUSTMENT"
        int quantity_change "signed, CHECK <> 0"
        int stock_after "audit snapshot, CHECK >= 0"
        bigint unit_cost_paise "cost at movement time"
        string note
        timestamp movement_date "API movementDate"
        timestamp created_at
        timestamp updated_at
    }
    DELIVERY {
        bigint id PK
        string tracking_number UK "TRK-SIM-yyyyMMdd-XXXXXXXX"
        string delivery_type "STORE_TO_CUSTOMER|CUSTOMER_TO_REPAIRER|REPAIRER_TO_CUSTOMER"
        bigint repair_request_id FK "nullable - repair legs; UK with type"
        bigint order_external_id "ext order-service - no FK; UK with type"
        string reference_number "RR-... or ORD-..."
        bigint customer_account_id "ext - buyer or repair customer"
        bigint partner_account_id "ext - seller or repairer"
        bigint booked_by_account_id
        string origin_code "demo location snapshot"
        double origin_latitude
        double origin_longitude
        string destination_code "demo location snapshot"
        double destination_latitude
        double destination_longitude
        double route_distance_km "straight line x road factor"
        double current_latitude "simulated"
        double current_longitude "simulated"
        int simulation_step "0..7"
        int progress_percent
        string status "BOOKED|PICKED_UP|IN_TRANSIT|NEAR_DESTINATION|DELIVERED"
        timestamp estimated_arrival_at
        timestamp picked_up_at
        timestamp delivered_at
        timestamp last_advanced_at
        bigint version "optimistic lock"
        timestamp created_at
        timestamp updated_at
    }
    DELIVERY_LOCATION_POINT {
        bigint id PK
        bigint delivery_id FK "UK with sequence_no"
        int sequence_no "0 = booking"
        string status
        double latitude "simulated"
        double longitude "simulated"
        int progress_percent
        double remaining_km
        string note
        bigint recorded_by_account_id
        timestamp recorded_at "append-only"
    }
```

## 4) Cross-service references (external IDs only — never FK)

```mermaid
flowchart LR
    subgraph PS["product-service :8081"]
        ACC["Account / CustomerAccount"]
        ADDR["Address"]
        PRD["Product"]
        RAI["RepairerAccountIdentity"]
    end
    subgraph OS["order-service :8082"]
        PO["PurchaseOrder"]
        RQ["RepairQuote"]
        PT["PaymentTransaction"]
    end
    subgraph RS["repair-service :8083"]
        RP["RepairerProfile"]
        RR["RepairRequest"]
        DL["Delivery (simulated)"]
    end
    PO -- "buyer/seller_account_id (no FK)" --> ACC
    RQ -- "customer_account_id" --> ACC
    RQ -- "repairer_account_id (payouts)" --> ACC
    PO -- "product_external_id (reserve / confirm-sale / release over REST)" --> PRD
    PT -- "provider_account_id" --> ACC
    RQ -- "repair_request_id (ext)" --> RR
    RR -- "customer_account_id" --> ACC
    RR -- "delivery_address_id (ext)" --> ADDR
    RR -- "latest_quote_id (ext)" --> RQ
    RR -- "payment_transaction_id (ext)" --> PT
    PT -- "repair_quote_id" --> RQ
    RP -- "account_external_id" --> ACC
    RAI -- "repairer_profile_id (ext)" --> RP
    OS -. "OrderItem.product_external_id" .-> PRD
    DL -- "order_external_id (GET /api/internal/orders/{id} at booking)" --> PO
    DL -- "customer/partner_account_id" --> ACC
```
