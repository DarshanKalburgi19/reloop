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

```mermaid
erDiagram
    PURCHASE_ORDER ||--o{ ORDER_ITEM : contains
    PURCHASE_ORDER ||--o{ PAYMENT_TRANSACTION : "attempt(s)"
    REPAIR_QUOTE ||--o{ PAYMENT_TRANSACTION : "attempt(s)"
    PAYMENT_TRANSACTION ||--o| COMMISSION_RECORD : "on SUCCESS"
    PAYMENT_TRANSACTION ||--o| PROVIDER_PAYOUT : "on SUCCESS"
    PAYMENT_TRANSACTION ||--o{ REFUND_RECORD : "0..n refunds"

    PURCHASE_ORDER {
        bigint id PK
        string order_number UK "uk_orders_number"
        bigint buyer_account_id "ext product-service - indexed"
        bigint seller_account_id "ext product-service - indexed"
        string status "enum PENDING|PAID|CANCELLED|REFUNDED - indexed"
        bigint subtotal_paise
        decimal customer_fee_rate "frozen 0.0200"
        bigint customer_fee_paise
        bigint total_paise "subtotal + customer fee"
        string currency
        timestamp paid_at
        timestamp cancelled_at
        timestamp created_at
        timestamp updated_at
    }
    ORDER_ITEM {
        bigint id PK
        bigint order_id FK "indexed"
        bigint product_external_id "ext product-service - indexed"
        string product_title_snapshot
        bigint unit_price_paise
        int quantity
        bigint line_total_paise
        timestamp created_at
        timestamp updated_at
    }
    REPAIR_QUOTE {
        bigint id PK
        bigint repair_request_id "ext repair-service - UK with quote_revision"
        bigint repairer_profile_id "ext repair-service - indexed"
        bigint customer_account_id "ext product-service"
        int quote_revision "1,2,3..."
        string status "enum DRAFT|SENT|APPROVED|REJECTED|SUPERSEDED"
        string summary
        bigint base_amount_paise
        decimal customer_fee_rate "frozen 0.0200"
        bigint customer_fee_paise
        bigint total_payable_paise "base + customer fee"
        decimal repairer_commission_rate "frozen 0.0200"
        bigint repairer_commission_paise
        bigint repairer_net_paise "base - commission"
        timestamp sent_at
        timestamp approved_at
        timestamp rejected_at
        timestamp valid_until
        timestamp created_at
        timestamp updated_at
    }
    PAYMENT_TRANSACTION {
        bigint id PK
        string transaction_id UK "NEVER duplicated"
        bigint purchase_order_id FK "nullable - exactly one ref (CHECK)"
        bigint repair_quote_id FK "nullable - exactly one ref (CHECK)"
        bigint buyer_account_id "ext product-service - indexed"
        bigint provider_id "ext seller/repairer - indexed"
        string provider_type "enum SELLER|REPAIRER"
        string method "enum UPI|CARD|NET_BANKING|WALLET"
        string status "enum PENDING|SUCCESS|FAILURE|CANCELLED - indexed"
        bigint base_amount_paise
        decimal customer_fee_rate "rate at transaction time"
        bigint customer_fee_paise
        bigint total_charged_paise "base + customer fee"
        decimal repairer_commission_rate "rate at transaction time"
        bigint repairer_commission_paise
        bigint provider_net_paise "base - commission"
        string failure_reason
        timestamp attempted_at
        timestamp completed_at
        timestamp created_at
        timestamp updated_at
    }
    COMMISSION_RECORD {
        bigint id PK
        bigint payment_transaction_id UK "one per txn"
        string transaction_id UK "business lookup"
        string reference_type "PURCHASE_ORDER|REPAIR_QUOTE"
        bigint reference_id
        decimal customer_fee_rate "frozen"
        bigint customer_fee_paise
        decimal provider_commission_rate "frozen"
        bigint provider_commission_paise
        bigint total_commission_paise "both fees = ReLoop revenue"
        string status "enum RECORDED|REVERSED"
        timestamp created_at
        timestamp updated_at
    }
    PROVIDER_PAYOUT {
        bigint id PK
        string payout_reference UK
        bigint payment_transaction_id UK "one per txn"
        bigint provider_id "indexed - external"
        string provider_type "enum SELLER|REPAIRER"
        bigint provider_account_id
        bigint gross_amount_paise
        bigint commission_paise
        bigint net_amount_paise "gross - commission"
        string status "enum PENDING|PAID|ON_HOLD|REVERSED - indexed"
        timestamp paid_at
        timestamp created_at
        timestamp updated_at
    }
    REFUND_RECORD {
        bigint id PK
        string refund_reference UK
        bigint payment_transaction_id "indexed"
        bigint order_id
        bigint refund_amount_paise
        bigint reversed_commission_paise
        string status "enum INITIATED|COMPLETED|FAILED - indexed"
        string reason "enum ORDER_CANCELLED|DISPUTED_ORDER|CUSTOMER_REQUEST"
        timestamp completed_at
        timestamp created_at
        timestamp updated_at
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
    REPAIR_REQUEST ||--o{ DELIVERY_TRACKING : shipments
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
    DELIVERY_TRACKING {
        bigint id PK
        bigint repair_request_id FK "indexed with status"
        string tracking_number "indexed"
        string carrier
        string status "CREATED..DELIVERED|FAILED"
        string origin_location
        string destination_location
        double current_latitude
        double current_longitude
        string event_note
        timestamp estimated_delivery_at
        timestamp delivered_at
        timestamp created_at
        timestamp updated_at
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
    end
    PO -- "buyer/seller_account_id (no FK)" --> ACC
    RQ -- "customer_account_id" --> ACC
    RQ -- "repair_request_id (ext)" --> RR
    RR -- "customer_account_id" --> ACC
    RR -- "delivery_address_id (ext)" --> ADDR
    RR -- "latest_quote_id (ext)" --> RQ
    RR -- "payment_transaction_id (ext)" --> PT
    PT -- "repair_quote_id" --> RQ
    RP -- "account_external_id" --> ACC
    RAI -- "repairer_profile_id (ext)" --> RP
    OS -. "OrderItem.product_external_id" .-> PRD
```
