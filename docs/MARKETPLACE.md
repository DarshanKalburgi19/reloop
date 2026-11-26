# Second-hand marketplace (product-service, port 8081)

Customers list used products for resale and buy products listed by other customers.
Every physical item carries a **product passport**: a unique code plus an append-only
history that follows the item across sellers.

- Code: `product-service/src/main/java/com/reloop/product/` — `controller/ProductController`,
  `service/ProductService`, `service/ProductSpecifications`, `entity/{Product,ProductPassport,ProductHistoryEvent}`,
  `seed/DemoProductSeeder`.
- Auth, roles and tokens: see [AUTH.md](AUTH.md). Tables: see [SCHEMA.md](SCHEMA.md) and [ER_DIAGRAM.md](ER_DIAGRAM.md).

## 1. API

| Method | Path | Who | Result |
|---|---|---|---|
| `GET` | `/api/products` | anyone | Paged browse/search of **ACTIVE** listings |
| `GET` | `/api/products/{id}` | anyone (hidden listings: seller only) | Full product details |
| `GET` | `/api/products/{id}/passport` | anyone (hidden listings: seller only) | Passport code + full history |
| `POST` | `/api/products` | CUSTOMER | Create a listing → `201` + `Location` |
| `PUT` | `/api/products/{id}` | CUSTOMER, seller only | Edit own listing (full replacement); can also deactivate/reactivate |
| `DELETE` | `/api/products/{id}` | CUSTOMER, seller only | Deactivate own listing (soft delete) → `204` |
| `GET` | `/api/products/mine?page=&size=` | CUSTOMER | Own listings in every status, newest first |
| `POST` | `/api/products/{id}/purchase` | CUSTOMER | Legacy direct purchase without fees. **410 Gone** unless `RELOOP_DIRECT_PURCHASE_ENABLED=true`; buy through order-service instead ([PAYMENTS.md](PAYMENTS.md)) |
| `POST` | `/api/internal/products/{id}/reserve` · `confirm-sale` · `release` | order-service (`X-Internal-Api-Key`) | ACTIVE → RESERVED for one order; RESERVED → SOLD (passport moves to the buyer); or back to ACTIVE. Idempotent per order reference |

Anonymous visitors can browse and view. REPAIRER accounts can browse and view, but can't
list or buy (`403`). Calls without a token to CUSTOMER endpoints get `401`.

### Product fields

| Requirement | JSON field (`ProductResponse`) | Notes |
|---|---|---|
| Product ID | `id` | |
| Seller ID | `sellerId`, `sellerName` | Seller email/phone are **never** exposed |
| Title / Description | `title`, `description` | 3–200 / up to 4000 chars |
| Category | `category` | `ELECTRONICS, FURNITURE, FASHION, BOOKS, HOME_KITCHEN, SPORTS_TOYS, APPLIANCES, OTHER` |
| Price | `price` (rupees), `pricePaise`, `currency` | ₹1.00 – ₹1,00,00,000, max 2 decimals; stored as integer paise |
| Condition | `condition` | `LIKE_NEW, VERY_GOOD, GOOD, FAIR, NEEDS_REPAIR` |
| City | `city` | Known cities are canonicalised (`"  surat "` → `Surat`) |
| Store / pickup location | `store {id,name,addressLine,city}`, `pickupLocation` | Store is optional; must be the seller's own ACTIVE store |
| Images | `images[] {url, altText, primary, placeholder}` | Up to 8; first is primary |
| Availability status | `availability`, `available` | `ACTIVE`, `RESERVED`, `SOLD`, `ARCHIVED` (`DRAFT` reserved for later) |
| Product passport code | `passportCode` | `RLP-XXXX-XXXX-XXXX` |
| Created date | `createdAt` (+ `updatedAt`, `soldAt`) | |

### Create / edit body

```json
{
  "title": "Used Sony headphones",
  "description": "Good sound, light scratches on the headband.",
  "category": "ELECTRONICS",
  "price": 2499.50,
  "condition": "GOOD",
  "city": "Surat",
  "pickupLocation": "Adajan, near Star Bazaar",
  "storeId": null,
  "imageUrls": ["/demo-images/placeholders/electronics.svg"],
  "passportCode": null
}
```

- `PUT` takes the same fields (without `passportCode`) plus an optional `status`, which may only be
  `ACTIVE` (reactivate) or `ARCHIVED` (deactivate). Any other value → `400`.
- `passportCode` (create only) relists an item you bought; see §3.
- Validation errors → `400` with `fieldErrors`, e.g. `{"fieldErrors":{"price":"must be greater than or equal to 1.00"}}`.

### Browse, search and filter — `GET /api/products`

| Param | Meaning |
|---|---|
| `q` | Keywords, case-insensitive, in title **or** description; every word must match (max 5 words). `%` and `_` are matched literally. |
| `category` | A `ProductCategory` |
| `city` | Exact city, case-insensitive |
| `condition` | A `ProductCondition` |
| `minPrice`, `maxPrice` | Rupees, inclusive. `minPrice > maxPrice` → `400` (`fieldErrors.priceRangeValid`) |
| `sort` | `newest` (default), `oldest`, `price_asc`, `price_desc` |
| `page`, `size` | 0-based page (default 0); size 1–50 (default 20) |

Filters combine with AND. Unknown enum values or out-of-range numbers → `400`.
The response is a stable page shape:
`{"content":[...],"page":0,"size":20,"totalElements":14,"totalPages":1,"first":true,"last":true}`;
each `content` item is a compact card with `thumbnailUrl`, `storeName` and `passportCode`.

## 2. Rules

| Rule | Behaviour |
|---|---|
| Edit/delete someone else's listing | `403` ("You can only edit/delete your own listings"). Non-owners get `404` for hidden (ARCHIVED) listings, so hidden listings stay hidden. |
| Delete | **Soft delete**: availability becomes `ARCHIVED` and a `DEACTIVATED` event is recorded. Idempotent. The passport history is never lost. |
| Reactivate | `PUT` with `"status":"ACTIVE"`. Refused (`409`) if you no longer own the item or it already has another open listing. |
| Sold / reserved listings | Frozen: edit or delete → `409`. |
| **Buying your own listing** | `403` "You cannot purchase your own listing". |
| **Buying an unavailable listing** | `409` "Product is not available for purchase (availability: SOLD)" (or ARCHIVED, RESERVED). |
| Two buyers at once | The purchase locks the product row (`SELECT … FOR UPDATE`): exactly one wins, the others get `409`. |
| Visibility | Browse/search show only `ACTIVE`. Details and passport are public for `ACTIVE`, `RESERVED`, `SOLD`. |

### Purchase scope

`POST /api/products/{id}/purchase` marks the listing `SOLD`, records the buyer and `soldAt`,
transfers the passport to the buyer and appends a `SOLD` event. It returns `productId`, `sellerId`,
`buyerId`, `price`, `passportCode`, `purchasedAt`. It takes no payment and charges no fees, so it is now
**disabled by default** (410). The real flow is `POST /api/orders` on order-service, which reserves the listing
(`RESERVED`, history event `RESERVED`). A successful simulated payment then confirms the sale (`SOLD`, passport
transferred). An unpaid order that is cancelled or expires releases the listing again (`RESERVATION_RELEASED`).
Sellers can't edit a `RESERVED` listing (409). See [PAYMENTS.md](PAYMENTS.md).

## 3. Product passport

- One passport = **one physical item**. The code (`RLP-` + 12 Crockford base32 characters, e.g.
  `RLP-7K3Q-X9M2-B4TD`) comes from `SecureRandom` (60 bits), so codes can't be guessed. It has no I,
  L, O or U, so it is easy to read from a label. It is unique (`uk_passports_code`) and immutable.
- History events (append-only): `LISTED`, `RELISTED`, `UPDATED` (lists the changed fields),
  `PRICE_CHANGED` ("Price changed from ₹1,300.00 to ₹1,100.00"), `DEACTIVATED`, `REACTIVATED`, `SOLD`.
- **Resale keeps the passport:** after buying, the new owner creates a listing with
  `"passportCode":"RLP-…"`. The listing starts with `RELISTED` and the passport shows the whole life of
  the item across both owners. Only the current owner may do this (`403` otherwise); an item can
  have only one open listing (`409`); unknown codes → `400`.
- `GET /api/products/{id}/passport` returns `passportCode`, `issuedAt`, `listingCount`,
  `ownershipTransfers`, `listingIds` and `history[] {eventType, occurredAt, productId, actorRole, price, details}`.
  For privacy, actors appear only as `SELLER` or `BUYER`, never by name or email.

## 4. Images (no real hosting yet)

Image upload/hosting is out of scope for this phase.

- Listings without `imageUrls` get a bundled SVG placeholder per category, served by product-service:
  `/demo-images/placeholders/{electronics,furniture,fashion,books,home-kitchen,sports-toys,appliances,other}.svg`
  (public, e.g. http://localhost:8081/demo-images/placeholders/books.svg).
- `imageUrls` accepts only `/demo-images/...` paths (no `..`) or `https://` URLs, so `javascript:`,
  `data:` and similar URLs are rejected. Files live in `product-service/src/main/resources/static/demo-images/`.

## 5. Demo data

Seeded when `reloop.seed.enabled=true` (the default in `application.properties`, off in tests),
after the demo accounts. The seeder is idempotent: it skips if the demo listings already exist.

**Demo customers** (TEMPORARY, local only — full list in [AUTH.md](AUTH.md)):

| Email | Password | Who |
|---|---|---|
| `customer.demo@reloop.local` | `ReLoopDemo#Cust2026` | Asha Patel, Ahmedabad |
| `karan.demo@reloop.local` | `ReLoopDemo#Karan2026` | Karan Desai, Surat — store "Karan's Pre-Loved Electronics" |
| `nisha.demo@reloop.local` | `ReLoopDemo#Nisha2026` | Nisha Trivedi, Vadodara |

**15 demo listings across 5 cities** (14 ACTIVE + 1 SOLD):

| City | Seller | Listings |
|---|---|---|
| Ahmedabad | Asha | Apple iPhone 12 (₹28,500), Sheesham study table (₹4,200), Prestige pressure cooker (**SOLD**) |
| Surat | Karan | Sony WH-1000XM4 (₹13,999, store), Dell Latitude 7490 (₹24,000, store), Banarasi silk saree (₹3,800) |
| Vadodara | Nisha | Hero Sprint bicycle (₹5,500), NCERT book set (₹900), LG washing machine (₹3,000, NEEDS_REPAIR) |
| Rajkot | Nisha | Godrej almirah (₹6,500), Canon EOS 1500D (₹21,000), Wooden toy kitchen (₹1,800) |
| Gandhinagar | Asha | Kindle Paperwhite (₹6,000), Philips air fryer (₹4,500), Cricket kit (₹2,200) |

The pressure cooker shows a real passport: `LISTED` at ₹1,300 → `PRICE_CHANGED` to ₹1,100 → `SOLD` to Karan.
All demo listings use the category placeholder images.

## 6. curl walkthrough

```bash
B=http://localhost:8081
login() { curl -s $B/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"email\":\"$1\",\"password\":\"$2\"}" | sed -E 's/.*"accessToken":"([^"]+)".*/\1/'; }
ASHA=$(login customer.demo@reloop.local 'ReLoopDemo#Cust2026')
NISHA=$(login nisha.demo@reloop.local 'ReLoopDemo#Nisha2026')

# Browse, search, filter (no token needed)
curl -s "$B/api/products?city=Rajkot"
curl -s "$B/api/products?q=sony&category=ELECTRONICS&minPrice=5000&maxPrice=20000&sort=price_asc"

# Create a listing as Asha
curl -si $B/api/products -H "Authorization: Bearer $ASHA" -H 'Content-Type: application/json' -d '{
  "title":"Bajaj table fan","description":"400 mm, all 3 speeds work","category":"APPLIANCES",
  "price":850,"condition":"GOOD","city":"ahmedabad","pickupLocation":"Navrangpura"}'
ID=...   # from the Location header / "id"

# Edit (price drop -> PRICE_CHANGED event), then view details and passport
curl -s -X PUT $B/api/products/$ID -H "Authorization: Bearer $ASHA" -H 'Content-Type: application/json' -d '{
  "title":"Bajaj table fan","description":"400 mm, all 3 speeds work","category":"APPLIANCES",
  "price":700,"condition":"GOOD","city":"Ahmedabad","pickupLocation":"Navrangpura"}'
curl -s $B/api/products/$ID
curl -s $B/api/products/$ID/passport

# Legacy direct purchase. It only works with RELOOP_DIRECT_PURCHASE_ENABLED=true (otherwise 410);
# normally buy through order-service (docs/PAYMENTS.md §6).
# Asha can't buy her own listing (403); Nisha can (200); a second purchase is refused (409)
curl -si -X POST $B/api/products/$ID/purchase -H "Authorization: Bearer $ASHA"
curl -s  -X POST $B/api/products/$ID/purchase -H "Authorization: Bearer $NISHA"
curl -si -X POST $B/api/products/$ID/purchase -H "Authorization: Bearer $NISHA"

# Nisha relists it with the same passport (history continues: ... SOLD, RELISTED)
CODE=RLP-...   # passportCode from the purchase response
curl -s $B/api/products -H "Authorization: Bearer $NISHA" -H 'Content-Type: application/json' -d "{
  \"title\":\"Bajaj table fan\",\"description\":\"Still works well\",\"category\":\"APPLIANCES\",
  \"price\":650,\"condition\":\"FAIR\",\"city\":\"Vadodara\",\"passportCode\":\"$CODE\"}"

# Deactivate (soft delete) and list own listings
curl -si -X DELETE $B/api/products/<listing-id> -H "Authorization: Bearer $NISHA"
curl -s $B/api/products/mine -H "Authorization: Bearer $NISHA"
```

## 7. Tests

`product-service/src/test/java/com/reloop/product/`:

| Test | Covers |
|---|---|
| `marketplace/ProductApiTest` | Create (all fields, placeholder image, privacy, city canonicalisation), validation, roles, store rules, details/404, SVG serving, edit + history, ownership `403`, status rules, soft delete, reactivate, `/mine` |
| `marketplace/ProductBrowseTest` | ACTIVE-only browse, keyword search (title/description, multi-word, literal `%`/`_`), every filter, combined filters, sorting, paging, invalid parameters |
| `marketplace/ProductPurchaseTest` | Purchase success, own product `403`, sold/deactivated `409`, `401`/`403` for anonymous/repairer, frozen sold listings, concurrent buyers, relisting with the same passport, owner-only relist, one open listing per item |
| `service/PassportCodeGeneratorTest`, `service/MarketplaceHelpersTest` | Code format/uniqueness/collision retry, money conversion, keyword parsing, LIKE escaping, placeholders |
| `seed/DemoAccountSeederTest`, `seed/DemoAccountSeederIntegrationTest` | Demo listings pass API validation, all 5 cities seeded, idempotent, cooker passport `LISTED → PRICE_CHANGED → SOLD` |
| `repository/AccountRepositoryTest` | Unique passport code constraint |

Run: `mvn -f product-service/pom.xml verify`.
