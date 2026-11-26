#!/usr/bin/env bash
# Scenario 1: marketplace sale. Seller lists → buyer orders → simulated payment SUCCESS.
# Checks: order CONFIRMED on both sides, product SOLD (product-service), commission + payout (order-service),
# seller net, buyer receipt, and that a replayed payment request (same Idempotency-Key) changes nothing.
source "$(dirname "$0")/lib.sh"
section "1. Marketplace sale ($BASE_URL)"
login_all

list_product 1000.00
info "product #$PRODUCT_ID listed at ₹1000.00"
place_order
expect_eq "$ORDER_TOTAL" "102000" "order total (₹1000 + 2% customer fee) in paise"

KEY="$(idem sale)"
pay "$BUYER" "{\"orderId\":$ORDER_ID}" "$ORDER_TOTAL" SUCCESS "$KEY"
expect_status '201|200' "buyer pays order $ORDER_NUMBER"
expect_eq "$TXN_STATUS" "SUCCESS" "transaction status"
expect_eq "$(j .providerCommissionPaise)" "2000" "provider commission (2%)"
expect_eq "$(j .providerNetPaise)" "98000" "seller net"
FIRST_TXN="$TXN"

# duplicate request (double click / network retry) → same transaction, nothing new
pay "$BUYER" "{\"orderId\":$ORDER_ID}" "$ORDER_TOTAL" SUCCESS "$KEY"
expect_status '200|201' "replayed payment request"
expect_eq "$TXN" "$FIRST_TXN" "replay returns the same transaction"

api GET "/api/orders/$ORDER_ID" "$BUYER" ''
expect_eq "$(j .status)" "CONFIRMED" "buyer sees order status"

api GET "/api/orders?role=seller" "$SELLER" ''
expect_eq "$(j --argjson id "$ORDER_ID" '.[] | select(.id == $id) | .status')" "CONFIRMED" "seller sees the sale"
expect_eq "$(j --argjson id "$ORDER_ID" '.[] | select(.id == $id) | .sellerNetPaise')" "98000" "seller sees net earning"

api GET "/api/products/$PRODUCT_ID" "$SELLER" ''
expect_eq "$(j .availability)" "SOLD" "product-service marked the product"

PO="$(payout_for "$FIRST_TXN" "$SELLER" "$SELLER_ID")"
expect_eq "$(printf '%s' "$PO" | jq -s 'length')" "1" "exactly one payout for the transaction"
expect_eq "$(printf '%s' "$PO" | jq -r '.currentNetPaise')" "98000" "payout net"

api GET "/api/customers/$BUYER_ID/payments" "$BUYER" ''
expect_eq "$(j --arg t "$FIRST_TXN" '[.payments[] | select(.transactionId == $t)] | length')" "1" "buyer payment history has the payment once"

api GET "/api/transactions/$FIRST_TXN/receipt?format=html" "$BUYER" '' -H 'Accept: text/html'
expect_status 200 "buyer can open the receipt"

finish
