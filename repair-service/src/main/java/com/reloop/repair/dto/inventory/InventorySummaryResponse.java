package com.reloop.repair.dto.inventory;

import com.reloop.repair.enums.SparePartCategory;
import java.math.BigDecimal;
import java.util.List;

/**
 * {@code GET /api/inventory/summary}: the repairer's inventory dashboard.
 *
 * <p>{@code inventoryValue} is the purchase cost of the stock on hand (Σ quantity × unit cost). It is an asset
 * figure, computed only from inventory data, and is NOT part of repair earnings (those are the payouts recorded
 * by order-service). {@code includedInRepairEarnings} is always false to make that explicit.</p>
 *
 * @param totalParts               active parts (distinct items)
 * @param discontinuedParts        parts marked discontinued
 * @param totalUnits               units on hand across all parts
 * @param lowStockCount            active parts with quantity ≤ threshold (includes out of stock)
 * @param outOfStockCount          active parts with quantity 0
 * @param stockPurchasedCostPaise  Σ cost of all STOCK_IN movements (lifetime)
 * @param partsUsedInRepairsCostPaise Σ cost of all USED_IN_REPAIR movements (lifetime)
 */
public record InventorySummaryResponse(
        Long repairerId,
        int totalParts,
        int discontinuedParts,
        long totalUnits,
        int lowStockCount,
        int outOfStockCount,
        boolean lowStockAlert,
        long inventoryValuePaise,
        BigDecimal inventoryValue,
        String inventoryValueDisplay,
        long stockPurchasedCostPaise,
        long partsUsedInRepairsCostPaise,
        boolean includedInRepairEarnings,
        String valuationNote,
        List<CategorySummary> byCategory) {

    /** Per-category totals, ordered by value (highest first). */
    public record CategorySummary(SparePartCategory category, int parts, long units, long valuePaise,
                                  BigDecimal value) {
    }
}
