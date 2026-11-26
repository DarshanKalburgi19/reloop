#!/usr/bin/env bash
# Shared helpers for the ReLoop end-to-end scenarios (bash + curl + jq).
#
# Every call goes through ONE base URL, the frontend dev server (frontend/server.js), which proxies
#   /api/auth, /api/products, /api/repairers…   → product-service (8081)
#   /api/orders, /api/payments, /api/transactions… → order-service (8082)
#   /api/repair-requests, /api/inventory, /api/tracking → repair-service (8083)
# exactly like the browser does. With `node server.js --mock` the same scripts run against the in-memory mock.
#
#   BASE_URL      default http://localhost:8080
#   E2E_VERBOSE=1 print every request / response body
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
E2E_VERBOSE="${E2E_VERBOSE:-0}"
PASS=0
FAIL=0
RUN_ID="$(date +%s)-$$"

for tool in curl jq; do
  command -v "$tool" >/dev/null || { echo "e2e: '$tool' is required" >&2; exit 2; }
done

c_green=$'\033[32m'; c_red=$'\033[31m'; c_dim=$'\033[2m'; c_bold=$'\033[1m'; c_off=$'\033[0m'
if [[ ! -t 1 ]]; then c_green=''; c_red=''; c_dim=''; c_bold=''; c_off=''; fi

section() { printf '\n%s== %s%s\n' "$c_bold" "$*" "$c_off"; }
info()    { printf '%s   %s%s\n' "$c_dim" "$*" "$c_off"; }
pass()    { PASS=$((PASS + 1)); printf '  %sPASS%s %s\n' "$c_green" "$c_off" "$*"; }
fail()    { FAIL=$((FAIL + 1)); printf '  %sFAIL%s %s\n' "$c_red" "$c_off" "$*"; }
die()     { printf '%sABORT%s %s\n' "$c_red" "$c_off" "$*" >&2; summary; exit 1; }

# Last response: $STATUS (HTTP code), $BODY (raw body), $HEADERS (raw headers)
STATUS=''; BODY=''; HEADERS=''

# api METHOD PATH [TOKEN] [JSON] [extra curl args…]
api() {
  local method="$1" path="$2" token="${3:-}" data="${4:-}"
  shift 4 2>/dev/null || shift $#
  local args=(-sS -X "$method" "$BASE_URL$path" -H 'Accept: application/json' -D - --max-time 20)
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n "$data" ]] && args+=(-H 'Content-Type: application/json' --data "$data")
  local raw
  raw="$(curl "${args[@]}" "$@")" || die "curl failed: $method $path (is the stack running at $BASE_URL?)"
  # headers and body are separated by the first blank line (CRLF)
  HEADERS="$(printf '%s' "$raw" | awk 'BEGIN{RS="\r\n\r\n"} NR==1{print; exit}')"
  BODY="$(printf '%s' "$raw" | awk 'BEGIN{RS="\r\n\r\n"; ORS=""} NR>1{if (NR>2) printf "\r\n\r\n"; print}')"
  STATUS="$(printf '%s' "$HEADERS" | awk 'toupper($1) ~ /^HTTP/ {code=$2} END{print code}')"
  if [[ "$E2E_VERBOSE" == "1" ]]; then
    info "$method $path → $STATUS"
    [[ -n "$data" ]] && info "  request:  $data"
    info "  response: $(printf '%s' "$BODY" | head -c 600)"
  fi
}

# header NAME → value of a response header of the last call (case-insensitive)
header() { printf '%s' "$HEADERS" | awk -v h="$(echo "$1" | tr 'A-Z' 'a-z')" -F': ' 'tolower($1)==h {sub(/\r$/,"",$2); print $2}' | tail -1; }

# j FILTER → jq over the last body (raw output)
j() { printf '%s' "$BODY" | jq -r "$@"; }

expect_status() { # expect_status CODE[|CODE] description
  if [[ "$STATUS" =~ ^($1)$ ]]; then pass "$2 (HTTP $STATUS)"; else fail "$2: expected HTTP $1, got $STATUS: $(printf '%s' "$BODY" | head -c 300)"; return 1; fi
}
expect_eq() { # expect_eq ACTUAL EXPECTED description
  if [[ "$1" == "$2" ]]; then pass "$3 = $2"; else fail "$3: expected '$2', got '$1'"; fi
}
expect_true() { # expect_true JQ_FILTER description   (evaluated on the last body)
  if [[ "$(j "$1" 2>/dev/null)" == "true" ]]; then pass "$2"; else fail "$2 (jq: $1)"; fi
}

summary() {
  printf '\n%s%d passed, %d failed%s\n' "$c_bold" "$PASS" "$FAIL" "$c_off"
}
finish() { summary; [[ "$FAIL" -eq 0 ]]; }

# ------------------------------------------------------------------ domain helpers
DEMO_SELLER_EMAIL="${DEMO_SELLER_EMAIL:-customer.demo@reloop.local}"
DEMO_SELLER_PASSWORD="${DEMO_SELLER_PASSWORD:-ReLoopDemo#Cust2026}"
DEMO_BUYER_EMAIL="${DEMO_BUYER_EMAIL:-karan.demo@reloop.local}"
DEMO_BUYER_PASSWORD="${DEMO_BUYER_PASSWORD:-ReLoopDemo#Karan2026}"
DEMO_REPAIRER_EMAIL="${DEMO_REPAIRER_EMAIL:-repairer.demo@reloop.local}"
DEMO_REPAIRER_PASSWORD="${DEMO_REPAIRER_PASSWORD:-ReLoopDemo#Rep2026}"

# login EMAIL PASSWORD → sets TOKEN and ACCOUNT_ID
login() {
  api POST /api/auth/login '' "$(jq -nc --arg e "$1" --arg p "$2" '{email:$e, password:$p}')"
  [[ "$STATUS" == "200" ]] || die "login $1 failed (HTTP $STATUS). Demo accounts need reloop.seed.enabled=true: $BODY"
  TOKEN="$(j .accessToken)"; ACCOUNT_ID="$(j .account.id)"
}

login_all() {
  login "$DEMO_SELLER_EMAIL" "$DEMO_SELLER_PASSWORD";     SELLER="$TOKEN";   SELLER_ID="$ACCOUNT_ID"
  login "$DEMO_BUYER_EMAIL" "$DEMO_BUYER_PASSWORD";       BUYER="$TOKEN";    BUYER_ID="$ACCOUNT_ID"
  login "$DEMO_REPAIRER_EMAIL" "$DEMO_REPAIRER_PASSWORD"; REPAIRER="$TOKEN"; REPAIRER_ID="$ACCOUNT_ID"
  api GET /api/repairers/me "$REPAIRER" ''
  [[ "$STATUS" == "200" ]] || die "GET /api/repairers/me failed (HTTP $STATUS): $BODY"
  REPAIRER_PROFILE="$(j .id)"
  info "seller #$SELLER_ID, buyer #$BUYER_ID, repairer account #$REPAIRER_ID (profile #$REPAIRER_PROFILE)"
}

idem() { echo "e2e-$RUN_ID-$1"; }

# list_product PRICE_RUPEES → PRODUCT_ID (listed by the seller)
list_product() {
  api POST /api/products "$SELLER" "$(jq -nc --arg t "E2E item $RUN_ID $RANDOM" --arg p "$1" '{title:$t,
    description:"Created by scripts/e2e to exercise the cross-service flow.", category:"ELECTRONICS",
    price:($p|tonumber), condition:"GOOD", city:"Ahmedabad"}')"
  [[ "$STATUS" == "201" || "$STATUS" == "200" ]] || die "create product failed (HTTP $STATUS): $BODY"
  PRODUCT_ID="$(j .id)"
}

# place_order → ORDER_ID, ORDER_TOTAL (buyer orders PRODUCT_ID)
place_order() {
  api POST /api/orders "$BUYER" "{\"productId\":$PRODUCT_ID}"
  [[ "$STATUS" == "201" || "$STATUS" == "200" ]] || die "create order failed (HTTP $STATUS): $BODY"
  ORDER_ID="$(j .id)"; ORDER_TOTAL="$(j .totalPaise)"; ORDER_NUMBER="$(j .orderNumber)"
}

# pay TOKEN TARGET_JSON AMOUNT OUTCOME KEY → TXN (transactionId), TXN_STATUS
pay() {
  local body
  body="$(jq -nc --argjson t "$2" --argjson a "$3" --arg o "$4" '$t + {method:"UPI", outcome:$o, amountPaise:$a}')"
  api POST /api/payments/simulate "$1" "$body" -H "Idempotency-Key: $5"
  TXN="$(j '.transactionId // empty')"; TXN_STATUS="$(j '.status // empty')"
}

# repair_to_awaiting_payment AMOUNT_RUPEES → RR_ID, QUOTE_ID, QUOTE_TOTAL_PAISE
repair_to_awaiting_payment() {
  api POST /api/repair-requests "$SELLER" "$(jq -nc --argjson r "$REPAIRER_PROFILE" '{repairerProfileId:$r, category:"MOBILE",
    itemDescription:"E2E phone", problemDescription:"The screen flickers and the battery drains within two hours.",
    deliveryMethod:"IN_PERSON"}')"
  [[ "$STATUS" == "201" || "$STATUS" == "200" ]] || die "create repair request failed (HTTP $STATUS): $BODY"
  RR_ID="$(j .id)"
  api POST "/api/repair-requests/$RR_ID/accept" "$REPAIRER" '{"note":"Bring it in"}';  [[ "$STATUS" == "200" ]] || die "accept: $STATUS $BODY"
  api POST "/api/repair-requests/$RR_ID/inspect" "$REPAIRER" '{"findings":"Display connector loose, battery at 61% health."}'; [[ "$STATUS" == "200" ]] || die "inspect: $STATUS $BODY"
  api POST "/api/repair-requests/$RR_ID/quote" "$REPAIRER" "$(jq -nc --arg a "$1" '{amount:($a|tonumber), summary:"Reseat connector and replace battery.", estimatedDays:2}')"
  [[ "$STATUS" == "200" ]] || die "quote: $STATUS $BODY"
  api POST "/api/repair-requests/$RR_ID/quote/approve" "$SELLER" '{}'; [[ "$STATUS" == "200" ]] || die "approve quote: $STATUS $BODY"
  QUOTE_ID="$(j .quote.quoteId)"
  # QuoteResponse amounts are rupees (BigDecimal); payments are paise
  QUOTE_TOTAL_PAISE="$(j '(.quote.totalPayable * 100 | round)')"
}

# payout_for TXN TOKEN ACCOUNT_ID → prints the payout JSON for that transaction (or nothing)
payout_for() {
  api GET "/api/providers/$3/payouts" "$2" ''
  j --arg t "$1" '.payouts[]? | select(.transactionId == $t)'
}
