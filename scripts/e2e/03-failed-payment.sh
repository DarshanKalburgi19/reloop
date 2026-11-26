#!/usr/bin/env bash
# Scenario 3: failed payment. No order confirmed, no payout, and both sides see the failure.
source "$(dirname "$0")/lib.sh"
section "3. Failed payment ($BASE_URL)"
login_all

info "--- marketplace order"
list_product 500.00
place_order
pay "$BUYER" "{\"orderId\":$ORDER_ID}" "$ORDER_TOTAL" FAILED "$(idem fail-order)"
expect_status '201|200' "simulated payment is recorded"
expect_eq "$TXN_STATUS" "FAILED" "transaction status"
FAILED_TXN="$TXN"

api GET "/api/orders/$ORDER_ID" "$BUYER" ''
expect_eq "$(j .status)" "PENDING_PAYMENT" "buyer: order NOT confirmed"
expect_true '.lastPaymentFailure != null' "buyer sees the failure reason"
api GET "/api/orders?role=seller" "$SELLER" ''
expect_eq "$(j --argjson id "$ORDER_ID" '.[] | select(.id == $id) | .status')" "PENDING_PAYMENT" "seller: order NOT confirmed"
expect_true "[.[] | select(.id == $ORDER_ID) | .lastPaymentFailure != null] | first" "seller sees the failed attempt"
api GET "/api/products/$PRODUCT_ID" "$SELLER" ''
[[ "$(j .availability)" != "SOLD" ]] && pass "product not sold ($(j .availability))" || fail "product must not be SOLD"
expect_eq "$(payout_for "$FAILED_TXN" "$SELLER" "$SELLER_ID" | jq -s length)" "0" "no payout for the failed transaction"

info "--- repair quote"
repair_to_awaiting_payment 1200.00
pay "$SELLER" "{\"repairQuoteId\":$QUOTE_ID}" "$QUOTE_TOTAL_PAISE" FAILED "$(idem fail-repair)"
expect_eq "$TXN_STATUS" "FAILED" "transaction status"
FAILED_TXN="$TXN"
api GET "/api/repair-requests/$RR_ID" "$SELLER" ''
expect_eq "$(j .status)" "AWAITING_PAYMENT" "customer: repair NOT confirmed"
expect_eq "$(j .paymentStatus)" "FAILED" "customer sees payment failed"
api GET "/api/repair-requests/$RR_ID" "$REPAIRER" ''
expect_eq "$(j .paymentStatus)" "FAILED" "repairer sees payment failed"
expect_true '.lastPaymentFailure != null' "repairer sees the reason"
expect_eq "$(payout_for "$FAILED_TXN" "$REPAIRER" "$REPAIRER_ID" | jq -s length)" "0" "no repairer payout"

info "--- retry succeeds"
pay "$SELLER" "{\"repairQuoteId\":$QUOTE_ID}" "$QUOTE_TOTAL_PAISE" SUCCESS "$(idem fail-repair-retry)"
expect_eq "$TXN_STATUS" "SUCCESS" "second attempt"
api GET "/api/repair-requests/$RR_ID" "$REPAIRER" ''
expect_eq "$(j .status)/$(j .paymentStatus)" "PAID/PAID" "repairer sees the job paid after the retry"

finish
