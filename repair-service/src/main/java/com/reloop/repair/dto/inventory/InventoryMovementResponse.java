package com.reloop.repair.dto.inventory;

import com.reloop.repair.enums.InventoryMovementType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One stock movement.
 *
 * @param quantity       units moved (always positive)
 * @param quantityChange signed change (+ in, − out)
 * @param stockAfter     the part's quantity right after this movement
 * @param unitCostPaise  unit cost at the time of the movement
 * @param totalCostPaise quantity × unitCostPaise
 */
public record InventoryMovementResponse(
        Long id,
        Long repairerId,
        Long sparePartId,
        String sparePartName,
        String sku,
        Long repairRequestId,
        String repairRequestNumber,
        InventoryMovementType movementType,
        int quantity,
        int quantityChange,
        int stockAfter,
        long unitCostPaise,
        long totalCostPaise,
        BigDecimal totalCost,
        String note,
        LocalDateTime movementDate,
        LocalDateTime createdAt) {
}
