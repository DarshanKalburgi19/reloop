# Repairer Spare-Parts Inventory

Each repairer keeps a private stock of spare parts in **repair-service** (port 8083). They can add and edit
parts, receive stock, record the parts used in a repair, correct the quantity, see low-stock alerts, and see
the value of their stock. The value of stock is reported separately from repair earnings.

| Piece | Where |
|-------|-------|
| Entities | `entity/SparePart.java` (`spare_parts`), `entity/InventoryMovement.java` (`inventory_movements`) |
| Enums | `InventoryMovementType` (STOCK_IN, USED_IN_REPAIR, ADJUSTMENT), `SparePartCategory`, `SparePartStatus`, `StockStatus` |
| Rules | `service/InventoryService.java` |
| API | `controller/InventoryController.java`, DTOs in `dto/inventory/` |
| Tests | `inventory/InventoryApiTest`, `inventory/InventoryConcurrencyTest`, `service/InventoryRulesTest`, `repository/RepairSchemaTest` |

## 1. Data model

### SparePart

| API field | Column | Notes |
|-----------|--------|-------|
| `id` | `id` | |
| `repairerId` | `repairer_profile_id` | The owner's workshop profile id. FK `fk_parts_profile`. |
| `partName` | `name` | Required, up to 150 characters. |
| `category` | `category` | `SparePartCategory`: SCREEN_DISPLAY, BATTERY, CHARGING_POWER, CAMERA, AUDIO, CIRCUIT_BOARD, STORAGE_MEMORY, KEYBOARD_INPUT, CABLES_CONNECTORS, MECHANICAL, CONSUMABLES, OTHER. |
| `quantity` | `stock_quantity` | Units on hand. CHECK `>= 0`. Changes only through movements. |
| `unitCost` / `unitCostPaise` | `unit_cost_paise` | Purchase cost per unit. Stored as integer paise; the API also returns rupees and a display string (`"₹450"`). |
| `lowStockThreshold` | `reorder_level` | Default 5. The part is low on stock when `quantity <= lowStockThreshold`. |
| `sku` | `sku` | Optional. If left out, one is generated (`BAT-7F3K2Q9A`). Stored upper-case. UNIQUE per repairer (`uk_parts_profile_sku`). |
| `description` | `description` | Optional. |
| `status` | `status` | ACTIVE or DISCONTINUED. Parts are never deleted, so their history is kept. |
| `createdAt`, `updatedAt` | `created_at`, `updated_at` | Set by `BaseEntity`. |

Derived fields in responses:
- `stockStatus`: IN_STOCK, LOW_STOCK or OUT_OF_STOCK.
- `lowStock`: true or false.
- `stockValuePaise`: quantity × unit cost.

The original column names are kept. The API names map onto them, so existing databases need no column rename.

### InventoryMovement: the stock ledger

Movements are append-only. Each row is one change to a part's quantity.

| API field | Column | Notes |
|-----------|--------|-------|
| `id` | `id` | |
| `repairerId` | `repairer_profile_id` | Always the part's owner. FK `fk_movements_profile`. |
| `sparePartId` | `spare_part_id` | |
| `repairRequestId` | `repair_request_id` | Required for USED_IN_REPAIR. Optional for ADJUSTMENT, for example when an unused part comes back from a job. |
| `movementType` | `movement_type` | STOCK_IN, USED_IN_REPAIR or ADJUSTMENT. |
| `quantity` | `abs(quantity_change)` | Units moved. `quantityChange` is the signed value. CHECK `<> 0`. |
| `movementDate` | `movement_date` | When the stock moved. Defaults to the time the row is created. A STOCK_IN can be back-dated but not set in the future. |
| `stockAfter` | `stock_after` | The balance after this movement, kept for auditing. |
| `unitCostPaise` | `unit_cost_paise` | The unit cost at the time of the movement, so later price changes don't alter past costs. |
| `note` | `note` | Required for ADJUSTMENT, where it is sent as `reason`. |

## 2. Rules

| # | Rule | How it is enforced |
|---|------|--------------------|
| 1 | The quantity never becomes negative. | Every stock change first locks the part row (`SELECT … FOR UPDATE`, `SparePartRepository.findOwnedForUpdate`). A change that would go below 0 is refused with **409** and `availableQuantity` / `requestedQuantity`. The entity refuses it as well (`SparePart.changeQuantity`), and the database has a CHECK constraint. The quantity is capped at 1,000,000 units. Info edits take the same lock, so they can't overwrite a quantity that another request has just changed. |
| 2 | Only the owner can change their stock. | `/api/inventory/**` requires the REPAIRER role: anonymous callers get **401** and customers get **403**. Every query is limited to the caller's own workshop profile, so another repairer's part or repair request returns **404**, which means ids can't be probed. A repairer without a workshop profile gets **404**. |
| 3 | Every stock change is recorded. | Quantities change in one place only (`InventoryService.record`), and it always writes a movement. `PATCH` cannot change the quantity, and an unknown `quantity` field is ignored. Refused changes are not recorded. The sum of a part's `quantityChange` values always equals its quantity. |
| 4 | Using parts in a repair reduces stock. | `POST /parts/{id}/use` reduces the quantity and links the movement to the repair request. The request must be assigned to the caller's workshop and be **PAID** or **IN_PROGRESS**, because work starts only after payment (see REPAIR_WORKFLOW.md). Any other status returns **409** with `currentStatus`. |
| 5 | Low-stock alert. | `GET /low-stock` lists active parts with `quantity <= lowStockThreshold`, most urgent first, with `lowStockAlert` and a message. Each stock change also returns `lowStockAlert` and `alertMessage`. Discontinued parts never raise an alert. |
| 6 | Inventory value is kept separate from repair earnings. | `GET /summary` computes `inventoryValuePaise` = Σ quantity × unit cost from inventory tables only. It never reads quotes, payments, commissions or payouts, and it returns `includedInRepairEarnings: false`. Repair earnings remain the payout records in order-service. |

### Unit cost on stock-in

If a delivery includes a `unitCost`, the part's unit cost becomes the **weighted average** of the stock on hand
and the delivery, rounded half-up to whole paise:

```
(onHand × currentCost + received × deliveryCost) / (onHand + received)
```

For example, 10 units at ₹100 plus 10 units at ₹120 gives 20 units at ₹110.

The movement stores the delivery price. If the delivery has no `unitCost`, the current cost is used.

Parts used in a repair, and adjustments, are costed at the part's current unit cost.

## 3. API

All endpoints need `Authorization: Bearer <token>` from product-service with role **REPAIRER**.

Money in requests is in rupees, with up to 2 decimals. Error responses use the usual `ApiError` shape
(`timestamp, status, error, message, path, fieldErrors`).

| Method | Path | Body / query | Result |
|--------|------|--------------|--------|
| GET | `/api/inventory/parts` | `?category=&q=&lowStockOnly=&includeDiscontinued=&sort=name\|quantity\|newest\|updated&page=&size≤100` | Page of parts. `q` matches the name or SKU and ignores case. |
| POST | `/api/inventory/parts` | `{partName, category, unitCost, quantity?, lowStockThreshold?, sku?, description?}` | **201** with a `Location` header. If the opening quantity is above 0, a STOCK_IN movement with the note "Opening stock" is recorded. |
| GET | `/api/inventory/parts/{id}` | | The part. |
| PATCH | `/api/inventory/parts/{id}` | Any of `{partName, category, sku, description, unitCost, lowStockThreshold}` | The updated part. The quantity is not editable here. |
| POST | `/api/inventory/parts/{id}/discontinue` | | Hidden from the default list. Stock can't be received, but remaining units can still be used. |
| POST | `/api/inventory/parts/{id}/reactivate` | | Active again. |
| POST | `/api/inventory/parts/{id}/stock-in` | `{quantity ≥ 1, unitCost?, note?, movementDate?}` | `StockChangeResponse` (STOCK_IN). |
| POST | `/api/inventory/parts/{id}/use` | `{repairRequestId, quantity ≥ 1, note?}` | `StockChangeResponse` (USED_IN_REPAIR). |
| POST | `/api/inventory/parts/{id}/adjust` | `{newQuantity ≥ 0` **or** `quantityChange ≠ 0, reason, repairRequestId?}` | `StockChangeResponse` (ADJUSTMENT). |
| GET | `/api/inventory/parts/{id}/movements` | `?page=&size=` | One part's ledger, newest first. |
| GET | `/api/inventory/movements` | `?type=&sparePartId=&repairRequestId=&from=YYYY-MM-DD&to=YYYY-MM-DD&page=&size=` | All movements, newest first. |
| GET | `/api/inventory/low-stock` | | `{lowStockAlert, count, outOfStockCount, alertMessage, items[]}` |
| GET | `/api/inventory/summary` | | Totals, alert flag, inventory value, value by category, and lifetime costs of purchases and of parts used in repairs. |
| GET | `/api/inventory/repair-requests/{id}/parts` | | Parts recorded against one of your repairs, with the net units used and their net cost. |

`StockChangeResponse` is `{part, movement, lowStockAlert, alertMessage}`.

### Status codes

| Code | When |
|------|------|
| 400 | Validation errors: missing or negative values, more than 2 decimals, a future `movementDate`, both or neither of `newQuantity` / `quantityChange`, an adjustment that changes nothing, an empty PATCH, an unknown `sort`, or `from` after `to`. |
| 401 | No token, or an invalid token. |
| 403 | The caller is not a REPAIRER. |
| 404 | The part or repair request doesn't exist or belongs to someone else, or the caller has no workshop profile. |
| 409 | Not enough stock, a duplicate SKU, stock-in on a discontinued part, a repair that is not PAID or IN_PROGRESS, or the row is locked by a concurrent request. |

### Summary example

```json
{
  "repairerId": 12, "totalParts": 3, "discontinuedParts": 1, "totalUnits": 15,
  "lowStockCount": 2, "outOfStockCount": 1, "lowStockAlert": true,
  "inventoryValuePaise": 769900, "inventoryValue": 7699.00, "inventoryValueDisplay": "₹7,699",
  "stockPurchasedCostPaise": 809900, "partsUsedInRepairsCostPaise": 40000,
  "includedInRepairEarnings": false,
  "valuationNote": "Inventory value is the purchase cost of the parts you have on hand (quantity x unit cost). It is tracked separately and is not included in your repair earnings.",
  "byCategory": [{"category": "SCREEN_DISPLAY", "parts": 1, "units": 2, "valuePaise": 399900, "value": 3999.00}, "..."]
}
```

## 4. curl walkthrough

```bash
TOKEN=$(curl -s localhost:8081/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"repairer.demo@reloop.local","password":"ReLoopDemo#Rep2026"}' | jq -r .accessToken)
H="Authorization: Bearer $TOKEN"

# Add a part with opening stock
curl -s localhost:8083/api/inventory/parts -H "$H" -H 'Content-Type: application/json' \
  -d '{"partName":"Redmi Note 12 battery","category":"BATTERY","unitCost":450,"quantity":10,"lowStockThreshold":3}'

# Receive 5 more units at ₹480 each (the unit cost becomes the weighted average)
curl -s localhost:8083/api/inventory/parts/1/stock-in -H "$H" -H 'Content-Type: application/json' \
  -d '{"quantity":5,"unitCost":480,"note":"Invoice #A-17"}'

# Use 1 unit on repair request 7 (must be PAID or IN_PROGRESS and assigned to you)
curl -s localhost:8083/api/inventory/parts/1/use -H "$H" -H 'Content-Type: application/json' \
  -d '{"repairRequestId":7,"quantity":1}'

# Set the quantity after a stock count
curl -s localhost:8083/api/inventory/parts/1/adjust -H "$H" -H 'Content-Type: application/json' \
  -d '{"newQuantity":12,"reason":"Monthly stock count"}'

curl -s localhost:8083/api/inventory/low-stock -H "$H"
curl -s localhost:8083/api/inventory/summary -H "$H"
```

The demo repairer needs a workshop profile first (`POST /api/repairers`; see REPAIRERS.md).

## 5. Tests

| Class | What it covers |
|-------|----------------|
| `InventoryApiTest` (MockMvc + H2) | Adding parts, with and without opening stock. Generated, normalised and duplicate SKUs. Validation errors. Info edits that never touch the quantity. Stock-in with weighted-average cost and back-dating. Usage linked to a repair, and usage refused for the wrong repair status or someone else's repair. Never-negative checks for usage and adjustments. Adjustments by `newQuantity` and by `quantityChange`, with a required reason. A full ledger whose running balance matches the quantity. Movement filters and date ranges. The low-stock list and alerts. The summary's value, counts and earnings separation. List filters, sort and paging limits. Discontinue and reactivate. Owner-only access (404 for other repairers, 403 for customers, 401 for anonymous callers, 404 for a repairer without a profile). The enum values. |
| `InventoryConcurrencyTest` | 12 parallel calls each take 1 unit of a 5-unit part. The row lock prevents overselling: the quantity ends at `5 − successes` and is never below 0, and the ledger matches. |
| `InventoryRulesTest` | `changeQuantity` bounds, stock status and threshold, valuation, weighted-average rounding, movement cost, the SKU generator, and SKU normalisation. |
| `RepairSchemaTest` | The CHECK constraints (no negative stock, no zero movement), SKU uniqueness, and the default movement date. |

Builds and tests run in GitHub Actions (`.github/workflows/ci.yml`). The commit statuses `tests/<service>`
report the totals.
