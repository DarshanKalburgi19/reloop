package com.reloop.repair.dto.inventory;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code GET /api/inventory/repair-requests/{id}/parts}: parts recorded against one repair.
 *
 * @param netUnitsUsed   units used minus units returned via adjustments linked to the repair
 * @param partsCostPaise net cost of those units at the unit cost recorded on each movement
 */
public record RepairPartsUsageResponse(Long repairRequestId, String requestNumber, int netUnitsUsed,
                                       long partsCostPaise, BigDecimal partsCost,
                                       List<InventoryMovementResponse> movements) {
}
