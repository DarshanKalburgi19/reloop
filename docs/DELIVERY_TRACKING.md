# Delivery tracking (simulated)

repair-service (port **8083**, the "Repair & Tracking" service) tracks three kinds of delivery: marketplace
purchases going from the seller to the buyer, items sent in for repair, and repaired items going back. Each delivery
has a tracking ID, an origin and destination on a map of Gujarat, a moving marker, a status, an estimated arrival
time and a saved history of every position.

> **Everything here is a simulation.** Origins and destinations are **predefined Gujarat demo locations**, not real
> addresses. The marker position is **calculated by ReLoop** (a straight-line interpolation between the two points)
> and moves only when someone presses the demo **advance** button. No GPS device, phone location or courier API is
> used or contacted. Every API response includes `"simulated": true` and a `simulationNotice`, tracking IDs look like
> `TRK-SIM-…`, the carrier is called "ReLoop Demo Courier (simulated)", and the demo page shows a SIMULATED banner and
> map label. Any frontend must keep these labels visible (see §7).

---

## 1. The three delivery legs

| `deliveryType` | Linked to | The two parties (only they can see it) | Who books it | When it can be booked | Default ends |
|---|---|---|---|---|---|
| `STORE_TO_CUSTOMER` | order-service order (`orderId`) | buyer (*customer*) and seller (*partner*) | the **seller** | order is `CONFIRMED` (paid), checked through order-service `GET /api/internal/orders/{id}` | none: send both `originCode` and `destinationCode` |
| `CUSTOMER_TO_REPAIRER` | repair request (`repairRequestId`) | the customer and the repairer who owns the assigned workshop | customer **or** repairer | delivery method `SEND_ITEM` or `PICKUP_DROPOFF`, and the request is `ACCEPTED` (before inspection) | destination = demo location nearest the workshop; origin must be sent |
| `REPAIRER_TO_CUSTOMER` | repair request | same as above | the **repairer** | delivery method not `IN_PERSON`, and the request is `COMPLETED`, **or** `QUOTE_REJECTED` / `CANCELLED` after the inbound leg was `DELIVERED` (the workshop has the item) | origin = the workshop's demo location; destination = the inbound leg's origin, otherwise it must be sent |

- Each order or repair request can have **at most one leg of each type** (a second booking returns 409
  with the existing tracking ID; enforced by unique constraints too).
- `IN_PERSON` repairs have no delivery (409).
- Booking a leg never changes the repair request or order status. Delivery tracking only reports on the parcel.
- The parties are copied onto the delivery when it is booked (`customerAccountId`, `partnerAccountId`), so reading a
  delivery needs no call to another service.

## 2. What each delivery stores

| Requirement | Field(s) | Table |
|---|---|---|
| Tracking ID | `trackingNumber` = `TRK-SIM-yyyyMMdd-XXXXXXXX` (8 Crockford base-32 characters, unique) | `deliveries` |
| Order or repair request ID | `orderId` (order-service id, no FK) or `repairRequestId` (FK), plus `referenceNumber` (`ORD-…` / `RR-…`) | `deliveries` |
| Origin coordinates | `origin {code, label, city, latitude, longitude}`: a copy of the demo location | `deliveries.origin_*` |
| Destination coordinates | `destination {…}` | `deliveries.destination_*` |
| Current simulated coordinates | `currentPosition {latitude, longitude}` | `deliveries.current_*` |
| Status | `status` (+ `statusLabel`, `statusFlow`, `nextStatus`, `canAdvance`) | `deliveries.status` |
| Estimated arrival | `estimatedArrivalAt` (recomputed and stored at every step) | `deliveries.estimated_arrival_at` |
| Coordinate history | `history[]`: one point per step, append-only | `delivery_location_points` |

Also returned: `routeDistanceKm`, `remainingDistanceKm`, `progressPercent`, `bookedAt`, `pickedUpAt`, `deliveredAt`,
`lastUpdatedAt`, and `route` (the `[[lat,lng],[lat,lng]]` polyline for the map).

## 3. Statuses and the simulation

`BOOKED → PICKED_UP → IN_TRANSIT → NEAR_DESTINATION → DELIVERED`. Statuses only move forward. Each
`POST …/advance` moves the delivery to the next fixed checkpoint (`DeliverySimulator`):

| Step | Status | Progress | Marker | Remaining (Surat Adajan → Ahmedabad CG Road, 256.7 km) |
|---:|---|---:|---|---:|
| 0 | `BOOKED` | 0 % | origin | 256.7 km |
| 1 | `PICKED_UP` | 0 % | origin | 256.7 km |
| 2 | `IN_TRANSIT` | 20 % | ⅕ of the way | 205.4 km |
| 3 | `IN_TRANSIT` | 40 % | | 154.0 km |
| 4 | `IN_TRANSIT` | 60 % | | 102.7 km |
| 5 | `IN_TRANSIT` | 80 % | | 51.3 km |
| 6 | `NEAR_DESTINATION` | 92 % | | 20.5 km |
| 7 | `DELIVERED` | 100 % | destination | 0 km |

So a delivery reaches `DELIVERED` after **7 advances**. An 8th returns **409** (`currentStatus: "DELIVERED"`) and
saves nothing.

- **Position:** `origin + (destination − origin) × progress`, rounded to 6 decimals. The marker always moves
  closer to the destination and never leaves the straight line.
- **Distance:** great-circle distance × `reloop.tracking.road-factor` (1.25), an estimate of the road distance.
  Minimum 1 km.
- **Estimated arrival:** `now + remaining km ÷ reloop.tracking.simulated-speed-kmph` (40 km/h), plus
  `reloop.tracking.pickup-lead-minutes` (45) while `BOOKED`. When `DELIVERED` it is the delivery time. The value is
  calculated when a step happens and stored, so every viewer sees the same value. "Now" is the moment of the demo
  click, so pressing advance quickly makes the simulated journey faster than real time.
- **History:** each step adds one row (`sequence`, `status`, `latitude`, `longitude`, `progressPercent`,
  `remainingKm`, a "Simulated …" `note`, who pressed advance, `recordedAt`). Rows are never updated or deleted
  (`@Immutable`, unique `(delivery_id, sequence_no)`).
- **Concurrency:** `@Version` on `deliveries`. If both parties press advance at the same moment, one wins and the
  other gets 409 *"changed by someone else"*.

| Property | Env | Default |
|---|---|---|
| `reloop.tracking.simulated-speed-kmph` | `RELOOP_TRACKING_SPEED_KMPH` | `40` |
| `reloop.tracking.pickup-lead-minutes` | `RELOOP_TRACKING_PICKUP_LEAD_MINUTES` | `45` |
| `reloop.tracking.road-factor` | (none) | `1.25` |

## 4. Demo locations (`GET /api/tracking/demo-locations`, public)

Public landmarks or market areas with approximate coordinates (`DemoLocations`). The first ten match the
neighbourhoods of the demo workshops in [REPAIRERS.md](REPAIRERS.md). Codes are case-insensitive.

| Code | Place | City | Lat, Lng |
|---|---|---|---|
| `AHD_CG_ROAD` | CG Road, Navrangpura | Ahmedabad | 23.0276, 72.5577 |
| `AHD_MANINAGAR` | Jawahar Chowk, Maninagar | Ahmedabad | 22.9962, 72.6036 |
| `AHD_VASTRAPUR` | Vastrapur Lake, Vastrapur | Ahmedabad | 23.0395, 72.5293 |
| `GNR_SECTOR_21` | Sector 21 Shopping Centre | Gandhinagar | 23.2316, 72.6452 |
| `SURAT_ADAJAN` | Adajan Patiya | Surat | 21.1940, 72.7932 |
| `SURAT_VARACHHA` | Hirabaug Circle, Varachha Road | Surat | 21.2040, 72.8590 |
| `VADODARA_ALKAPURI` | R C Dutt Road, Alkapuri | Vadodara | 22.3105, 73.1690 |
| `VADODARA_FATEHGUNJ` | Fatehgunj Circle | Vadodara | 22.3235, 73.1846 |
| `RAJKOT_KALAWAD_ROAD` | KKV Chowk, Kalawad Road | Rajkot | 22.2840, 70.7705 |
| `BHAVNAGAR_WAGHAWADI` | Waghawadi Road | Bhavnagar | 21.7600, 72.1480 |
| `AHD_SATELLITE` | Shivranjani Cross Roads, Satellite | Ahmedabad | 23.0258, 72.5260 |
| `ANAND_VIDYANAGAR` | Vallabh Vidyanagar | Anand | 22.5530, 72.9230 |
| `MEHSANA_MODHERA_ROAD` | Modhera Road | Mehsana | 23.5880, 72.3693 |
| `JAMNAGAR_BEDI_GATE` | Bedi Gate | Jamnagar | 22.4707, 70.0577 |
| `JUNAGADH_KALWA_CHOWK` | Kalwa Chowk | Junagadh | 21.5222, 70.4579 |
| `BHUJ_STATION_ROAD` | Station Road | Bhuj | 23.2420, 69.6669 |

A workshop's demo location is the one **nearest its saved coordinates**. If it has none, the first location in its
city is used. If that fails too, the code must be sent. The demo repairer (Ravi Mehta, CG Road) maps to
`AHD_CG_ROAD`.

## 5. Access rules

| Situation | Result |
|---|---|
| No or invalid token (anything except `demo-locations` and the demo page) | **401** |
| Delivery, repair request or order you are not a party of (read, history, advance, booking) | **404** (existence is not revealed) |
| Party, but not allowed to book this leg (buyer booking the store leg, customer booking the return) | **403** |
| Advance after `DELIVERED`; leg already booked; order not paid; repair in the wrong status; `IN_PERSON` repair | **409** with `currentStatus` |
| Unknown demo code, same origin and destination, missing or extra `orderId`/`repairRequestId`, missing `deliveryType` | **400** with `fieldErrors` |
| order-service down while booking a store leg | **503** (nothing saved) |

**Both parties get identical data.** `GET /api/tracking/deliveries/{trackingNumber}` returns byte-for-byte the same
JSON to the customer and the repairer (or the buyer and the seller). Nothing in the response depends on the viewer,
and either party may press advance. Tests compare the two responses directly.

## 6. API reference (repair-service, port 8083)

| Method & path | Auth | Body | Returns |
|---|---|---|---|
| `GET /api/tracking/demo-locations` | public | — | `[{code,label,city,latitude,longitude}]` |
| `POST /api/tracking/deliveries` | token | `{deliveryType, repairRequestId? , orderId?, originCode?, destinationCode?}` | **201** `DeliveryResponse`, `Location: /api/tracking/deliveries/{trackingNumber}` |
| `GET /api/tracking/deliveries/mine` | token | — | summaries of the legs where you are a party, newest first |
| `GET /api/tracking/deliveries/{trackingNumber}` | party | — | `DeliveryResponse` (with history) |
| `GET /api/tracking/deliveries/{trackingNumber}/history` | party | — | `[TrackingPoint]`, oldest first |
| `POST /api/tracking/deliveries/{trackingNumber}/advance` | party | — | `DeliveryResponse` after the next checkpoint; 409 once delivered |
| `GET /api/tracking/repair-requests/{id}/deliveries` | repair participant | — | summaries (404 for others) |
| `GET /api/tracking/orders/{orderId}/deliveries` | token | — | summaries of the legs you are a party of (empty for others) |
| `GET /api/internal/orders/{id}` *(order-service 8082)* | `X-Internal-Api-Key` | — | `{id, orderNumber, status, buyerAccountId, sellerAccountId, productId, productTitle, paidAt}`. No amounts |

`DeliveryResponse` (Surat → Ahmedabad inbound repair leg after 3 advances):

```json
{
  "simulated": true,
  "simulationNotice": "SIMULATED DEMO TRACKING: locations are predefined Gujarat demo points and the position is generated by ReLoop's simulator. No GPS device or courier service is involved.",
  "trackingNumber": "TRK-SIM-20261125-7K3M9QXD",
  "deliveryType": "CUSTOMER_TO_REPAIRER",
  "deliveryTypeLabel": "Customer to repairer",
  "carrier": "ReLoop Demo Courier (simulated)",
  "status": "IN_TRANSIT",
  "statusLabel": "In transit",
  "statusFlow": ["BOOKED", "PICKED_UP", "IN_TRANSIT", "NEAR_DESTINATION", "DELIVERED"],
  "nextStatus": "IN_TRANSIT",
  "canAdvance": true,
  "repairRequestId": 12,
  "orderId": null,
  "referenceNumber": "RR-20261125-ABCDEFGH",
  "customerAccountId": 1,
  "partnerAccountId": 2,
  "origin": {"code": "SURAT_ADAJAN", "label": "Adajan Patiya", "city": "Surat", "latitude": 21.194, "longitude": 72.7932},
  "destination": {"code": "AHD_CG_ROAD", "label": "CG Road, Navrangpura", "city": "Ahmedabad", "latitude": 23.0276, "longitude": 72.5577},
  "currentPosition": {"latitude": 21.92744, "longitude": 72.699},
  "progressPercent": 40,
  "routeDistanceKm": 256.7,
  "remainingDistanceKm": 154.0,
  "estimatedArrivalAt": "2026-11-25T15:11:00",
  "bookedAt": "2026-11-25T10:00:00",
  "pickedUpAt": "2026-11-25T10:40:00",
  "deliveredAt": null,
  "lastUpdatedAt": "2026-11-25T11:20:00",
  "route": [[21.194, 72.7932], [23.0276, 72.5577]],
  "history": [
    {"sequence": 0, "status": "BOOKED", "latitude": 21.194, "longitude": 72.7932, "progressPercent": 0, "remainingKm": 256.7,
     "note": "Simulated booking created; pickup scheduled at Adajan Patiya, Surat.", "recordedAt": "2026-11-25T10:00:00"},
    {"sequence": 1, "status": "PICKED_UP", "latitude": 21.194, "longitude": 72.7932, "progressPercent": 0, "remainingKm": 256.7,
     "note": "Simulated pickup at Adajan Patiya, Surat.", "recordedAt": "2026-11-25T10:40:00"},
    {"sequence": 2, "status": "IN_TRANSIT", "latitude": 21.56072, "longitude": 72.7461, "progressPercent": 20, "remainingKm": 205.4,
     "note": "Simulated position: 20% of the 256.7 km route covered, 205.4 km to go.", "recordedAt": "2026-11-25T11:00:00"},
    {"sequence": 3, "status": "IN_TRANSIT", "latitude": 21.92744, "longitude": 72.699, "progressPercent": 40, "remainingKm": 154.0,
     "note": "Simulated position: 40% of the 256.7 km route covered, 154.0 km to go.", "recordedAt": "2026-11-25T11:20:00"}
  ]
}
```

## 7. Frontend integration

### 7.1 The bundled demo page

`http://localhost:8083/tracking-demo.html` is a single static page served by repair-service
(`src/main/resources/static/tracking-demo.html`, `tracking-demo/tracking-demo.js`, `tracking-demo/tracking-demo.css`).
It has no build step and loads **Leaflet 1.9.4** from unpkg (with SRI hashes) and **OpenStreetMap** tiles, so the
browser needs internet access. Without it, the page still shows the data but no map.

1. Start product-service, order-service and repair-service (README).
2. Get two tokens: `curl -s localhost:8081/api/auth/login -H 'Content-Type: application/json' -d '{"email":"customer.demo@reloop.local","password":"ReLoopDemo#Cust2026"}' | jq -r .accessToken`,
   and the same with `repairer.demo@reloop.local` / `ReLoopDemo#Rep2026`. The page cannot log in for you because
   product-service is on another origin and does not enable CORS.
3. Paste them as **Party A** and **Party B** and click **Save tokens** (they are kept in `sessionStorage` for that
   tab only).
4. Book a leg in the form, or load a tracking number. `?tracking=TRK-SIM-…` in the URL loads one straight away.
5. Press **Demo: advance**. The marker moves smoothly to the next simulated point, the blue history trail grows,
   and the stepper, ETA and history table update.
6. Switch between **View as A** and **View as B** to see that both parties get the same view. Tick
   **Auto-refresh (5 s)** to see the other party's advances appear.

The map shows all 16 demo locations as grey dots, the route as a dashed straight line ("approximate straight-line
route (simulated)"), the origin in green, the destination in red, and the parcel as a marker with the tooltip
"SIMULATED position". A "SIMULATED — not GPS" label sits on the map.

### 7.2 Building it into the ReLoop frontend (Phase 5)

1. **Same origin, relative URLs.** Serve the frontend behind a reverse proxy (or the dev server's proxy) that sends
   `/api/tracking/**` to repair-service `:8083`, and call `fetch('/api/tracking/…')`. Never call
   `http://localhost:8083` from browser code. repair-service has no CORS configuration, so a different origin would
   need one added to `SecurityConfig`.
2. **Auth:** send `Authorization: Bearer <JWT from product-service login>` on every call except `demo-locations`.
   Handle 401 (log in again), 404 ("not found": also what non-parties get) and 409 (show `message`).
3. **Where to link it:**
   - order details for the seller: a "Book delivery (simulated)" button that sends `{deliveryType:"STORE_TO_CUSTOMER", orderId, originCode, destinationCode}` once the order is `CONFIRMED`; buyer and seller then list `GET /api/tracking/orders/{id}/deliveries`;
   - repair request details: `GET /api/tracking/repair-requests/{id}/deliveries`. Offer "Send item in" while `ACCEPTED` (non-`IN_PERSON`) and "Book return" for the repairer once `COMPLETED`;
   - dashboards: `GET /api/tracking/deliveries/mine`.
4. **Origin and destination pickers:** populate them from `GET /api/tracking/demo-locations` (label them
   "Demo location"). Do not offer free-text addresses.
5. **Map (Leaflet + OSM):**

   ```html
   <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
         integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=" crossorigin="">
   <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"
           integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=" crossorigin=""></script>
   <div id="map" style="height:420px"></div>
   <script>
     const map = L.map('map').setView([22.4, 71.8], 7);                 // Gujarat
     L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
       maxZoom: 18, attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
     }).addTo(map);
     const layer = L.layerGroup().addTo(map);

     function draw(d) {                                                  // d = DeliveryResponse
       layer.clearLayers();
       L.polyline(d.route, {dashArray: '6 8', color: '#5b6773'}).addTo(layer);          // simulated straight route
       L.polyline(d.history.map(p => [p.latitude, p.longitude]), {color: '#1f6feb'}).addTo(layer);
       L.marker([d.currentPosition.latitude, d.currentPosition.longitude])
         .bindTooltip('SIMULATED position · ' + d.statusLabel).addTo(layer);
       map.fitBounds(L.latLngBounds(d.route).pad(0.25));
     }

     async function api(path, method = 'GET') {
       const res = await fetch('/api/tracking/' + path, {method, headers: {Authorization: 'Bearer ' + token}});
       if (!res.ok) throw new Error((await res.json()).message);
       return res.json();
     }
     // load:    draw(await api('deliveries/' + trackingNumber));
     // advance: draw(await api('deliveries/' + trackingNumber + '/advance', 'POST'));   // disable the button when !d.canAdvance
   </script>
   ```

6. **Keep it labelled.** Show `simulationNotice` (or an equivalent banner) next to every map. Call the button
   "Demo: advance" and the marker "simulated position". Never call it "live", "GPS" or a courier name. Render the
   stepper from `statusFlow`, `status` and `nextStatus`.
7. **Refreshing:** polling `GET /deliveries/{tn}` every 5–10 s is enough, because positions change only on advance.
   Insert API strings with `textContent`, not `innerHTML`.
8. **OpenStreetMap tile policy:** keep the attribution, and don't bulk-download or pre-fetch tiles. For anything
   beyond a demo, use a tile provider or your own tile server.

## 8. Try it with curl (all three services running)

```bash
J='Content-Type: application/json'
login() { curl -s localhost:8081/api/auth/login -H "$J" -d "{\"email\":\"$1\",\"password\":\"$2\"}" | jq -r .accessToken; }
CUST=$(login customer.demo@reloop.local 'ReLoopDemo#Cust2026')
REP=$(login repairer.demo@reloop.local 'ReLoopDemo#Rep2026')
T=localhost:8083/api/tracking

curl -s $T/demo-locations | jq -r '.[] | "\(.code)\t\(.city)"'

# Repair legs: book a SEND_ITEM repair and let the repairer accept it (docs/REPAIR_WORKFLOW.md has the full flow)
PROFILE=$(curl -s localhost:8083/api/repairers/me -H "Authorization: Bearer $REP" | jq .id)
ID=$(curl -s localhost:8083/api/repair-requests -H "Authorization: Bearer $CUST" -H "$J" -d "{\"repairerProfileId\":$PROFILE,
  \"category\":\"MOBILE\",\"itemDescription\":\"Galaxy S21\",\"problemDescription\":\"Screen cracked after a fall, touch dead\",
  \"deliveryMethod\":\"SEND_ITEM\"}" | jq .id)
curl -s -X POST localhost:8083/api/repair-requests/$ID/accept -H "Authorization: Bearer $REP" | jq .status    # ACCEPTED

# 1. customer sends the item from Surat; the destination defaults to the workshop (AHD_CG_ROAD)
TN=$(curl -s $T/deliveries -H "Authorization: Bearer $CUST" -H "$J" \
  -d "{\"deliveryType\":\"CUSTOMER_TO_REPAIRER\",\"repairRequestId\":$ID,\"originCode\":\"SURAT_ADAJAN\"}" | jq -r .trackingNumber)

# 2. demo-advance a few times (either party), watch the simulated marker move
for i in 1 2 3; do
  curl -s -X POST $T/deliveries/$TN/advance -H "Authorization: Bearer $REP" | \
    jq -c '{status,progressPercent,currentPosition,remainingDistanceKm,estimatedArrivalAt}'
done

# 3. both parties see the same thing
diff <(curl -s $T/deliveries/$TN -H "Authorization: Bearer $CUST") \
     <(curl -s $T/deliveries/$TN -H "Authorization: Bearer $REP") && echo "identical"
curl -s $T/deliveries/$TN/history -H "Authorization: Bearer $CUST" | jq -c '.[] | [.sequence,.status,.latitude,.longitude]'

# 4. finish it; one more advance is a 409
for i in 1 2 3 4; do curl -s -X POST $T/deliveries/$TN/advance -H "Authorization: Bearer $CUST" | jq -r .status; done
curl -s -X POST $T/deliveries/$TN/advance -H "Authorization: Bearer $CUST" | jq '{status,message,currentStatus}'

# 5. after the repair is COMPLETED (inspect → quote → approve → pay → progress → complete, see REPAIR_WORKFLOW.md),
#    the repairer books the return: origin = workshop, destination = where the item came from
curl -s $T/deliveries -H "Authorization: Bearer $REP" -H "$J" \
  -d "{\"deliveryType\":\"REPAIRER_TO_CUSTOMER\",\"repairRequestId\":$ID}" | jq '{trackingNumber,origin:.origin.code,destination:.destination.code}'
curl -s $T/repair-requests/$ID/deliveries -H "Authorization: Bearer $CUST" | jq

# Marketplace leg: after a paid order (docs/PAYMENTS.md §6: Nisha sells to Asha, OID = order id)
NISHA=$(login nisha.demo@reloop.local 'ReLoopDemo#Nisha2026')
curl -s $T/deliveries -H "Authorization: Bearer $NISHA" -H "$J" \
  -d "{\"deliveryType\":\"STORE_TO_CUSTOMER\",\"orderId\":$OID,\"originCode\":\"AHD_VASTRAPUR\",\"destinationCode\":\"AHD_MANINAGAR\"}" | \
  jq '{trackingNumber,status,referenceNumber,simulated}'
curl -s $T/orders/$OID/deliveries -H "Authorization: Bearer $CUST" | jq     # the buyer (Asha) sees it too

# then open http://localhost:8083/tracking-demo.html?tracking=$TN
```

## 9. Data model

`deliveries` replaces the unused Phase-1 `delivery_tracking` table, which had no coordinates for its ends and a
required repair link, so it could not hold marketplace legs.

| Table | Key columns and constraints |
|---|---|
| `deliveries` | **UNIQUE** `tracking_number`; `delivery_type` (30); nullable FK `repair_request_id` → `repair_requests`; `order_external_id` (order-service id, no FK); **UNIQUE** `(repair_request_id, delivery_type)` and `(order_external_id, delivery_type)`; `customer_account_id`, `partner_account_id`, `booked_by_account_id` (external ids); `origin_*` and `destination_*` (code, label, city, latitude, longitude); `route_distance_km`; `current_latitude/longitude`; `simulation_step`, `progress_percent`, `status`; `estimated_arrival_at`, `picked_up_at`, `delivered_at`, `last_advanced_at`; `version`. Indexes `ix_deliveries_customer`, `ix_deliveries_partner`, `ix_deliveries_status` |
| `delivery_location_points` | FK `delivery_id` → `deliveries`; **UNIQUE** `(delivery_id, sequence_no)`; `status`, `latitude`, `longitude`, `progress_percent`, `remaining_km`, `note`, `recorded_by_account_id`, `recorded_at`; append-only (`@Immutable`) |

Cross-service: repair-service → order-service `GET /api/internal/orders/{id}` (`X-Internal-Api-Key`), only when a
store leg is booked. There is no shared database.

## 10. Tests

| Test | Covers |
|---|---|
| repair-service `tracking/DeliveryTrackingApiTest` | **SIMULATED labels** (`simulated`, notice, carrier, `TRK-SIM-` id); inbound leg defaults to the workshop; **full status progression** with the marker getting closer at every step and ending on the destination; **history saved** (8 points, DB count); **advance after DELIVERED → 409**, nothing saved; **customer and repairer get identical JSON** (details and history), lists; outsiders → 404 (read, history, advance, per-request list, booking), anonymous → 401; `IN_PERSON` and wrong-status → 409; duplicate leg → 409; validation (unknown code, same ends, missing or extra ids, bad type) → 400; return leg (repairer only → 403 for the customer, defaults back to the inbound origin, QUOTE_REJECTED/CANCELLED rules); **store leg** (seller books a paid order, buyer sees the same JSON, buyer → 403, stranger → 404, unpaid → 409, unknown order → 404, order-service down → 503) |
| repair-service `service/DeliverySimulatorTest` (unit) | checkpoint order, linear interpolation and clamping, distance × road factor, ETA formula (lead, speed, delivered), a full run with history and the "already delivered" guard, config validation |
| repair-service `service/DemoLocationsTest` (unit) | all inside Gujarat, unique codes, case-insensitive lookup, nearest/city fallback |
| repair-service `repository/DeliverySchemaTest` | unique tracking number, one leg per type per order, unique history sequence, party query |
| repair-service `client/HttpOrderServiceClientTest` | `getOrder` contract (internal key, unknown fields ignored) and 404 mapping |
| order-service `payment/InternalOrderApiTest` | `GET /api/internal/orders/{id}`: fields, no amounts, CONFIRMED after payment, key required, 404 |

```bash
mvn -B -ntp -f repair-service/pom.xml verify
mvn -B -ntp -f order-service/pom.xml verify
```

## 11. Troubleshooting

- *Old dev database:* the dev H2 file may still contain the old empty `delivery_tracking` table. It is unused and
  harmless. Delete `repair-service/data/` for a clean schema (the demo workshops are seeded again).
- *The map is grey:* the browser can't reach unpkg.com or tile.openstreetmap.org. The tracking data still loads.
- *404 on a delivery you booked:* check which party token is selected. Only the two parties of a leg can see it.
- *503 when booking a store leg:* order-service isn't running, or `RELOOP_INTERNAL_API_KEY` differs between services.

## Not yet

Real courier integration (it would replace `DeliverySimulator` with carrier webhooks behind the same API). Road-snapped
routes (OSRM or another routing service). Failed or returned-to-sender statuses. Push updates (SSE or WebSocket)
instead of polling. QR labels for tracking IDs (ZXing).
