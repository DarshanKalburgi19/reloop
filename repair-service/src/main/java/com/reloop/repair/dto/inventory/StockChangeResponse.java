package com.reloop.repair.dto.inventory;

/**
 * Result of a stock movement: the updated part, the recorded movement and the low-stock alert.
 *
 * @param lowStockAlert true when the part is now at or below its threshold
 * @param alertMessage  human-readable alert, or null
 */
public record StockChangeResponse(SparePartResponse part, InventoryMovementResponse movement,
                                  boolean lowStockAlert, String alertMessage) {
}
