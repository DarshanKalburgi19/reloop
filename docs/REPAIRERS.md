# ReLoop — Repairer Service Providers

repair-service (port 8083) owns repairer workshop profiles. Repairers create and edit their own
workshop. Customers search workshops by city, category, price, repair method and distance, then
open a profile to see location, services, prices, hours and ratings.

All coordinates are **WGS84 decimal degrees**, the format Leaflet and OpenStreetMap use.

## 1. Endpoints

| Method | Path | Who | What |
|---|---|---|---|
| `POST` | `/api/repairers` | REPAIRER | Create your workshop profile. Returns **201** with a `Location` header, or **409** with `profileId` if you already have one. |
| `GET` | `/api/repairers` | public | Search with paging (see §3). |
| `GET` | `/api/repairers/nearby` | public | Workshops within `radiusKm` of `lat`/`lng`, nearest first (see §4). |
| `GET` | `/api/repairers/me` | REPAIRER | Your own profile in any state, including `missingForListing`. **404** if you have no profile. |
| `GET` | `/api/repairers/{id}` | public | Public workshop profile. |
| `PUT` | `/api/repairers/{id}` | REPAIRER (owner) | Edit your profile (full replacement, same body as `POST`). |
| `POST` | `/api/repairer-profiles` | internal | Used by product-service at repairer registration; needs `X-Internal-Api-Key`. Unchanged. |
| `GET` | `/api/repairer-profiles/{id}` | internal | Unchanged. |

### Authentication

Log in at product-service (`POST http://localhost:8081/api/auth/login`, see [AUTH.md](AUTH.md))
and send `Authorization: Bearer <accessToken>` to repair-service.

repair-service only **verifies** tokens. It checks the HS256 signature with the shared secret
`reloop.jwt.secret` (env `RELOOP_JWT_SECRET`), the issuer `reloop-product-service`, the expiry
and the role. product-service and repair-service must use the same secret.

| Situation | Status |
|---|---|
| No token, or an invalid, expired or foreign token on a protected endpoint | 401 |
| CUSTOMER token on `POST`/`PUT`/`me` | 403 |
| A repairer editing someone else's public profile | 403 |
| A repairer editing someone else's hidden profile (incomplete, inactive or suspended) | 404, so its existence isn't revealed |
| Editing a `SUSPENDED` profile | 409 |
| Invalid token on a public endpoint | Ignored; the request is served as anonymous |

## 2. Workshop profile

### Request body (`POST` and `PUT`)

```json
{
  "businessName": "Navrangpura Phone Lab",
  "description": "Phone and laptop repairs near CG Road with genuine parts and warranty.",
  "categories": ["MOBILE", "LAPTOP"],
  "expertise": ["Screen replacement", "Micro-soldering"],
  "services": [
    {"name": "Phone screen replacement", "category": "MOBILE", "priceType": "STARTING_FROM", "price": 1499, "estimatedDays": 1},
    {"name": "Battery replacement", "category": "MOBILE", "priceType": "FIXED", "price": 899.50},
    {"name": "Laptop OS reinstall", "category": "LAPTOP", "priceType": "FIXED", "price": 499}
  ],
  "phone": "+919812345678",
  "addressLine": "12 CG Road, Navrangpura",
  "city": "Ahmedabad",
  "pincode": "380009",
  "latitude": 23.0276,
  "longitude": 72.5577,
  "workingHours": [
    {"day": "MONDAY", "opens": "10:00", "closes": "19:00"},
    {"day": "SATURDAY", "opens": "10:00", "closes": "14:00"}
  ],
  "repairMethods": ["IN_PERSON", "SEND_ITEM", "PICKUP_DROPOFF"],
  "availability": "AVAILABLE",
  "availabilityNote": "Same-day screen repairs"
}
```

| Field | Rules |
|---|---|
| `businessName` | 3–150 characters |
| `description` | 20–2000 characters |
| `categories` | 1–8 of `MOBILE, LAPTOP, TABLET, TV, HOME_APPLIANCE, BICYCLE, ELECTRICAL, OTHER` |
| `expertise` | Optional. Up to 10 tags of 2–40 characters each, no commas or semicolons, no duplicates. |
| `services` | 1–30. `name` 3–150 characters and unique within the workshop. `category` must be one of the workshop's `categories`. `priceType` is `FIXED` or `STARTING_FROM`. `price` is in **rupees**, from 1 to 10,00,000, with at most 2 decimals. `estimatedDays` is 1–60. |
| `phone` | Optional. 10–15 digits, optionally starting with `+`. Shown publicly as the workshop's business phone. |
| `addressLine`, `city`, `pincode` | Required. `pincode` is a 6-digit Indian PIN. City case is normalised: `ahmedabad` becomes `Ahmedabad`. |
| `latitude`, `longitude` | Required and must be inside India (lat 6.5–37.5, lng 68–97.5). Swapped values get a specific error. Stored rounded to 6 decimals. |
| `workingHours` | 1–7 days. Times are 24-hour `HH:mm`, `closes` must be after `opens`, and each day may appear only once. Missing days are treated as closed. Overnight hours aren't supported. |
| `repairMethods` | At least one of `IN_PERSON` (walk in), `SEND_ITEM` (courier the item) and `PICKUP_DROPOFF` (the workshop collects and returns it) |
| `availability` | `AVAILABLE`, `BUSY` or `UNAVAILABLE`, plus an optional `availabilityNote` (up to 200 characters) |

Errors return **400** with `fieldErrors`. Keys are field paths such as `services[1].name` or
`workingHours[0].closes`.

### Editing (`PUT`)

- A `PUT` is a full replacement.
- To keep an existing service and its id, send its `id`. Services without an `id` are created.
- Existing services left out of the request become **INACTIVE** and are hidden. They aren't deleted, because repair requests may reference them.
- Sending an `id` that belongs to another workshop gives 400.
- Edits never change `verificationStatus`, `status`, ratings or the demo label.

### Response (`RepairerDetailResponse`)

Besides the request fields, the response includes:

- `id`, `services[]` (each with `id`, `price`, `pricePaise` and `priceLabel` such as `"From ₹1,499"` or `"₹899.50"`)
- `startingPrice` / `startingPriceLabel`: the cheapest active service
- `location: {lat, lng}`: pass it straight to Leaflet as `L.marker([location.lat, location.lng])`
- `mapUrl`: an OpenStreetMap link
- `workingHours[]` sorted Monday to Sunday, plus `workingHoursSummary`, for example `"Mon-Sat 10:00-19:00, Sun closed"` or `"Daily 09:00-21:00"`
- `rating: {average, count}` (`average` is `null` when there are no reviews) and `recentReviews` (up to 5)
- `verificationStatus` (always `UNVERIFIED` for new profiles; only ReLoop can verify), `status`
- `listedInSearch`, `missingForListing` (for example `["description", "services", "workingHours"]`), `ownedByViewer`
- `demo` and `demoNotice` (present only for demo data)

### When a profile is visible

A profile appears in search and on `GET /{id}` for everyone only when it is **ACTIVE and
complete**. Complete means it has a description, categories, at least one active priced
service, working hours, a PIN code, coordinates and at least one repair method. Otherwise only
its owner can see it, through `/me` or `/{id}`; everyone else gets 404.

Repairer **registration** (`POST /api/auth/register/repairer` on product-service) creates a
basic, incomplete profile. The repairer then completes it with
`PUT /api/repairers/{id}`, using the id from `GET /api/repairers/me`. A second `POST` returns
409 with that `profileId`.

## 3. Search — `GET /api/repairers`

| Param | Meaning |
|---|---|
| `city` | Exact city, case-insensitive |
| `category` | Workshops offering that category |
| `minPrice`, `maxPrice` | Rupees. The workshop must have at least one active service in the range, and in `category` when one is given. |
| `method` | `IN_PERSON`, `SEND_ITEM` or `PICKUP_DROPOFF` |
| `availableOnly` | `true` returns only `AVAILABLE` workshops |
| `q` | Keywords (up to 5) matched against name, description and expertise; all keywords must match |
| `sort` | `rating` (default: average, then count), `price_asc`, `price_desc` (by starting price), `name`, `newest` |
| `page`, `size` | 0-based; `size` defaults to 20, maximum 50 |

The response has the shape `{content[], page, size, totalElements, totalPages, first, last}`.

- Each card includes `location`, `categories`, `repairMethods`, `rating`, `availability`, `verificationStatus`, `demo` and `demoNotice`.
- The card's `startingPrice` is the cheapest service **matching the filters**. For example, with `category=LAPTOP&maxPrice=1000` it shows the cheapest laptop service up to ₹1,000.
- Invalid parameters (an unknown enum, `minPrice > maxPrice` reported as `fieldErrors.priceRangeValid`, `size > 50`, an unknown sort) return 400.

## 4. Nearby — `GET /api/repairers/nearby`

| Param | Meaning |
|---|---|
| `lat`, `lng` | Required. The search centre, for example from the browser's geolocation or a map click. |
| `radiusKm` | 0.5–100, default 10 |
| `category`, `minPrice`, `maxPrice`, `method`, `availableOnly` | Same as search |
| `limit` | 1–50, default 20 |

```json
{
  "center": {"lat": 23.0225, "lng": 72.5714},
  "radiusKm": 10.0,
  "bounds": {"southWest": {"lat": 22.93, "lng": 72.47}, "northEast": {"lat": 23.11, "lng": 72.67}},
  "count": 3,
  "results": [{"id": 1, "businessName": "QuickFix Mobile Care (Demo)", "distanceKm": 1.51, "location": {"lat": 23.0276, "lng": 72.5577}, "...": "..."}]
}
```

- Distances are great-circle (haversine) distances in km, not road distances.
- The database is first filtered with an indexed bounding box (`ix_profiles_lat_lng`), then the exact distance is checked.
- `bounds` can be passed straight to Leaflet as `map.fitBounds([[sw.lat, sw.lng], [ne.lat, ne.lng]])`.

```js
const r = await fetch(`/api/repairers/nearby?lat=${lat}&lng=${lng}&radiusKm=10`).then(r => r.json());
r.results.forEach(w => L.marker([w.location.lat, w.location.lng]).addTo(map)
  .bindPopup(`${w.businessName}<br>${w.startingPriceLabel ?? ''} · ${w.distanceKm} km`));
```

## 5. Ratings

Reviews are stored in `repairer_reviews` (rating 1–5, comment, reviewer display name). The
profile keeps a summary (`rating_average`, `rating_count`) that `RatingService.recalculate`
keeps in sync. That summary is what search sorts on.

In this phase only the demo seeder creates reviews. Customer reviews after a completed repair
will arrive with the repair-request flow.

## 6. Demo workshops — DEMO DATA

> Created when `reloop.seed.enabled=true` (the default in `application.properties`; off in
> tests). Each workshop has `demo: true` and a `demoNotice` in API responses, and every name
> ends with "(Demo)".
>
> - They are **fictional businesses** placed at real Gujarat locations.
> - Their phone numbers (`+9198000001xx`) are fake.
> - They use placeholder account ids −1001…−1010, so they have no login and can't be edited.
> - Seeding is idempotent.

| Workshop | City | Coordinates | Categories | Methods | Status | Rating |
|---|---|---|---|---|---|---|
| QuickFix Mobile Care (Demo) | Ahmedabad, CG Road | 23.0276, 72.5577 | Mobile, Tablet | In person, pickup | Available, verified | 4.8 (4) |
| Maninagar Laptop Clinic (Demo) | Ahmedabad, Maninagar | 22.9962, 72.6036 | Laptop, Tablet | In person, send | Available, verified | 4.3 (3) |
| Vastrapur Home Appliance Repairs (Demo) | Ahmedabad, Vastrapur | 23.0395, 72.5293 | Appliances, TV, Electrical | In person, pickup | **Busy** | 4.0 (4) |
| Sector 21 Gadget Doctor (Demo) | Gandhinagar | 23.2316, 72.6452 | Mobile, Laptop, TV | In person, send | Available, verified | 4.5 (2) |
| Adajan Smart Repairs (Demo) | Surat, Adajan | 21.1940, 72.7932 | Mobile, Laptop, Tablet | All three | Available, verified | 4.6 (5) |
| Varachha Cycle Works (Demo) | Surat, Varachha | 21.2040, 72.8590 | Bicycle | In person, pickup | Available | 4.5 (2) |
| Alkapuri TV & Electronics (Demo) | Vadodara, Alkapuri | 22.3105, 73.1690 | TV, Electrical, Appliances | In person, pickup | Available, verified | 4.3 (3) |
| Fatehgunj Fix Point (Demo) | Vadodara, Fatehgunj | 22.3235, 73.1846 | Mobile, Other | Send only | **Unavailable** | 3.5 (2) |
| Kalawad Road Mobile Hub (Demo) | Rajkot | 22.2840, 70.7705 | Mobile, Tablet, Laptop | All three | Available | 4.3 (3) |
| Waghawadi Electronics Service (Demo) | Bhavnagar | 21.7600, 72.1480 | Appliances, TV, Electrical | In person, pickup | Available | — (0) |

The demo **repairer account** `repairer.demo@reloop.local` / `ReLoopDemo#Rep2026` (created by
product-service, see [AUTH.md](AUTH.md)) owns "Mehta Fix-It Workshop (Demo)". Registration
marks it `demo: true`, but leaves it **incomplete**: log in as Ravi and complete it with `PUT` to
try the repairer flow (see §7).

Local databases seeded before this change keep the old, unlabelled name. To reseed, delete both
`product-service/data/` and `repair-service/data/`.

## 7. Try it (curl)

```bash
# Customers: search and map
curl -s 'http://localhost:8083/api/repairers?city=ahmedabad'
curl -s 'http://localhost:8083/api/repairers?category=LAPTOP&maxPrice=1000&sort=price_asc'
curl -s 'http://localhost:8083/api/repairers?method=SEND_ITEM&availableOnly=true'
curl -s 'http://localhost:8083/api/repairers/nearby?lat=23.0225&lng=72.5714&radiusKm=10'
curl -s 'http://localhost:8083/api/repairers/1'

# Repairer: log in at product-service, then manage the workshop at repair-service
TOKEN=$(curl -s -X POST http://localhost:8081/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"repairer.demo@reloop.local","password":"ReLoopDemo#Rep2026"}' | jq -r .accessToken)
curl -s http://localhost:8083/api/repairers/me -H "Authorization: Bearer $TOKEN"   # listedInSearch=false, missingForListing=[...]
ID=$(curl -s http://localhost:8083/api/repairers/me -H "Authorization: Bearer $TOKEN" | jq .id)
curl -s -X PUT http://localhost:8083/api/repairers/$ID -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d @workshop.json                             # body as in §2 -> listedInSearch=true

# A customer token can't create workshops -> 403; no token -> 401
```

## 8. Tests

| Test | Covers |
|---|---|
| `repairer/RepairerApiTest` | Create (201 + Location), 409 on a second profile, public and `/me` reads, owner-only edit (403/404), service keep/deactivate on `PUT`, the registration profile hidden until complete, 401 for missing, invalid, expired, foreign-secret and foreign-issuer tokens, 403 for customers, field and cross-field validation |
| `repairer/RepairerSearchApiTest` | Seeder (labelled, idempotent, Gujarat coordinates, ratings), city, category, price, method and availability filters, keywords, sorts and paging, invalid parameters, public detail, nearby radius, order, filters, limit and bounds |
| `repairer/DemoRepairersTest` | Every demo workshop passes the API's validation and is labelled |
| `service/GeoUtilsTest`, `MoneyTest`, `WorkingHoursFormatterTest`, `ProfileRequestRulesTest` | Haversine distance and bounding box, paise rounding and `₹` formatting, hours summaries, cross-field rules |
| `health/HealthControllerSliceTest` | Now runs with the real security rules |

## 9. Limitations

- Reviews can't be written by customers yet; they will come with completed repair requests.
- Verification (`VERIFIED`) and suspension are admin actions with no API yet.
- `price_asc`/`price_desc` sort by the workshop's overall starting price, not by the cheapest service matching a category filter. The card shows the filtered price.
- Nearby uses straight-line distance and a bounding-box pre-filter, which is fine at MVP scale. Use PostGIS or a geohash index for large data.
- Working hours don't support overnight shifts or holidays.
