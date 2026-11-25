package com.reloop.repair.enums;

/**
 * Derived stock level of a spare part (not stored).
 * <ul>
 *   <li>{@link #OUT_OF_STOCK}: quantity is 0.</li>
 *   <li>{@link #LOW_STOCK}: 0 &lt; quantity ≤ low-stock threshold.</li>
 *   <li>{@link #IN_STOCK}: quantity is above the threshold.</li>
 * </ul>
 */
public enum StockStatus {
    IN_STOCK,
    LOW_STOCK,
    OUT_OF_STOCK
}
