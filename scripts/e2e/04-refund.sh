#!/usr/bin/env bash
# Scenario 4: refund. A successful payment is refunded in full; the transaction, commission, payout, order,
# product passport (product-service), repair (repair-service) and the customer history all show the reversal.
# A repeated refund request with the same Idempotency-Key is replayed, never applied twice.
source "$(dirname "$0")/lib.sh"
section "4. Refund ($BASE_URL)"
login_all

info "--- marketplace sale refund"
list_product 800.00
place_order
pay "$BUYER" "{\"orderId\":$ORDER_ID}" "$ORDER_TOTAL" SUCCESS "$(idem refund-sale)"
expect_eq "$TXN_STATUS" "SUCCESS" "payment"
SALE_TXN="$TXN"

KEY="$(idem refund-1)"
api POST "/api/transactions/$SALE_TXN/refunds" "$BUYER" '{"reason":"CUSTOMER_REQUEST"}' -H "Idempotency-Key: $KEY"
expect_status 403 "the buyer cannot refund their own payment"
api POST "/api/transactions/$SALE_TXN/refunds" "$SELLER" '{"reason":"ITEM_NOT_AS_DESCRIBED","note":"e2e"}' -H "Idempotency-Key: $KEY"
expect_status '200|201' "seller issues a full refund"
expect_eq "$(j .status)" "REFUNDED" "transaction status"
expect_eq "$(j .refundedAmountPaise)" "$ORDER_TOTAL" "customer gets base + fee back"
expect_eq "$(j '.refunds | length')" "1" "one refund record"

api POST "/api/transactions/$SALE_TXN/refunds" "$SELLER" '{"reason":"ITEM_NOT_AS_DESCRIBED","note":"e2e"}' -H "Idempotency-Key: $KEY"
expect_status '200|201' "same refund request again"
expect_eq "$(header Idempotent-Replay)" "true" "answered as a replay"
expect_eq "$(j '.refunds | length')" "1" "still one refund record"

api POST "/api/transactions/$SALE_TXN/refunds" "$SELLER" '{"reason":"OTHER"}' -H "Idempotency-Key: $(idem refund-2)"
expect_status 409 "a new refund on a fully refunded payment is rejected"

api GET "/api/orders/$ORDER_ID" "$BUYER" ''
expect_eq "$(j .status)" "REFUNDED" "buyer sees the order"
PO="$(payout_for "$SALE_TXN" "$SELLER" "$SELLER_ID")"
expect_eq "$(printf '%s' "$PO" | jq -r .currentNetPaise)" "0" "seller payout reversed to 0"
expect_eq "$(printf '%s' "$PO" | jq -r .reversedCommissionPaise)" "1600" "commission reversed"
api GET "/api/customers/$BUYER_ID/payments" "$BUYER" ''
expect_eq "$(j --arg t "$SALE_TXN" '.payments[] | select(.transactionId == $t) | .status')" "REFUNDED" "buyer payment history"
api GET "/api/products/$PRODUCT_ID/passport" "$SELLER" ''
expect_true '[.history[]? | .eventType] | index("SALE_REFUNDED") != null' "product passport records SALE_REFUNDED (product-service)"

info "--- repair payment refund"
repair_to_awaiting_payment 1500.00
pay "$SELLER" "{\"repairQuoteId\":$QUOTE_ID}" "$QUOTE_TOTAL_PAISE" SUCCESS "$(idem refund-repair)"
expect_eq "$TXN_STATUS" "SUCCESS" "repair payment"
REPAIR_TXN="$TXN"
api POST "/api/transactions/$REPAIR_TXN/refunds" "$REPAIRER" '{"reason":"REPAIR_NOT_COMPLETED"}' -H "Idempotency-Key: $(idem refund-r)"
expect_status '200|201' "repairer refunds the paid job"
expect_eq "$(j .status)" "REFUNDED" "transaction status"
api GET "/api/repair-requests/$RR_ID" "$SELLER" ''
expect_eq "$(j .paymentStatus)" "REFUNDED" "customer sees the repair refunded"
expect_eq "$(j .status)" "CANCELLED" "paid-but-not-started repair is closed"
api GET "/api/repair-requests/$RR_ID" "$REPAIRER" ''
expect_eq "$(j .refundedAmountPaise)" "$QUOTE_TOTAL_PAISE" "repairer sees the refunded amount"
PO="$(payout_for "$REPAIR_TXN" "$REPAIRER" "$REPAIRER_ID")"
expect_eq "$(printf '%s' "$PO" | jq -r .currentNetPaise)" "0" "repairer payout reversed"

finish
