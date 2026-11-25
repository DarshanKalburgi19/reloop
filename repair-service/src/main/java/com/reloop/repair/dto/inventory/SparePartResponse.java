package com.reloop.repair.dto.inventory;

import com.reloop.repair.enums.SparePartCategory;
import com.reloop.repair.enums.SparePartStatus;
import com.reloop.repair.enums.StockStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A spare part as returned by the inventory API.
 *
 * @param repairerId      the owning repairer profile id
 * @param unitCostPaise   purchase cost per unit (integer paise); {@code unitCost} is the same in rupees
 * @param stockValuePaise quantity × unit cost
 * @param lowStock        true when quantity ≤ lowStockThreshold (the alert flag)
 */
public record SparePartResponse(
        Long id,
        Long repairerId,
        String sku,
        String partName,
        SparePartCategory category,
        String description,
        int quantity,
        long unitCostPaise,
        BigDecimal unitCost,
        String unitCostDisplay,
        int lowStockThreshold,
        StockStatus stockStatus,
        boolean lowStock,
        long stockValuePaise,
        BigDecimal stockValue,
        SparePartStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
