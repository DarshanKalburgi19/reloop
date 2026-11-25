package com.reloop.repair.dto.inventory;

import java.util.List;

/**
 * {@code GET /api/inventory/low-stock}: active parts at or below their threshold, most urgent first.
 *
 * @param lowStockAlert   true when there is at least one item
 * @param count           number of low-stock items (including out-of-stock ones)
 * @param outOfStockCount items with quantity 0
 */
public record LowStockResponse(boolean lowStockAlert, int count, int outOfStockCount, String alertMessage,
                               List<SparePartResponse> items) {
}
