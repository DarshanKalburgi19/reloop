#!/usr/bin/env bash
# Scenario 2: repair. Customer requests → repairer accepts, inspects, quotes → customer approves and pays.
# Checks: repair-service learns about the payment from order-service (PAID, paymentStatus, transactionId) on BOTH
# dashboards, the repairer's payout (gross / commission / net), inventory usage, completion visible to the customer.
source "$(dirname "$0")/lib.sh"
section "2. Repair flow ($BASE_URL)"
login_all

repair_to_awaiting_payment 2400.00
info "repair request #$RR_ID, quote #$QUOTE_ID, total payable $QUOTE_TOTAL_PAISE paise"
expect_eq "$QUOTE_TOTAL_PAISE" "244800" "quote total (₹2400 + 2% fee)"

pay "$SELLER" "{\"repairQuoteId\":$QUOTE_ID}" "$QUOTE_TOTAL_PAISE" SUCCESS "$(idem repair)"
expect_status '201|200' "customer pays the approved quote"
expect_eq "$TXN_STATUS" "SUCCESS" "transaction status"

api GET "/api/repair-requests/$RR_ID" "$SELLER" ''
expect_eq "$(j .status)" "PAID" "customer sees repair status"
expect_eq "$(j .paymentStatus)" "PAID" "customer sees payment status"
expect_eq "$(j .transactionId)" "$TXN" "same transaction id in repair-service"

api GET "/api/repair-requests/incoming?status=PAID&size=50" "$REPAIRER" ''
expect_eq "$(j --argjson id "$RR_ID" '.content[] | select(.id == $id) | .paymentStatus')" "PAID" "repairer sees the confirmed paid job"

PO="$(payout_for "$TXN" "$REPAIRER" "$REPAIRER_ID")"
expect_eq "$(printf '%s' "$PO" | jq -r .grossAmountPaise)" "240000" "repairer gross"
expect_eq "$(printf '%s' "$PO" | jq -r .commissionPaise)" "4800" "repairer commission (2%)"
expect_eq "$(printf '%s' "$PO" | jq -r .netAmountPaise)" "235200" "repairer net"

api POST "/api/repair-requests/$RR_ID/progress" "$REPAIRER" '{"note":"Started: battery out"}'
expect_status 200 "repairer starts the repair"

# inventory: use one unit of a part in stock (a part is created first when the workshop has none)
api GET "/api/inventory/parts?size=100" "$REPAIRER" ''
PART="$(j '[.content[]? | select(.status == "ACTIVE" and .quantity > 0)][0].id // empty')"
if [[ -z "$PART" ]]; then
  api POST /api/inventory/parts "$REPAIRER" '{"partName":"E2E battery","category":"BATTERY","unitCost":450.00,"quantity":5,"lowStockThreshold":1}'
  expect_status '201|200' "repairer adds a spare part to the inventory"
  PART="$(j .id)"
fi
api GET "/api/inventory/parts/$PART" "$REPAIRER" ''
QTY_BEFORE="$(j .quantity)"
api POST "/api/inventory/parts/$PART/use" "$REPAIRER" "{\"repairRequestId\":$RR_ID,\"quantity\":1,\"note\":\"e2e\"}"
expect_status '200|201' "repairer records part #$PART used on the paid job"
api GET "/api/inventory/parts/$PART" "$REPAIRER" ''
expect_eq "$(j .quantity)" "$((QTY_BEFORE - 1))" "stock decreased"

api POST "/api/repair-requests/$RR_ID/complete" "$REPAIRER" '{"note":"Done"}'
expect_status 200 "repairer completes the repair"
api GET "/api/repair-requests/$RR_ID" "$SELLER" ''
expect_eq "$(j .status)" "COMPLETED" "customer sees completion"

finish
