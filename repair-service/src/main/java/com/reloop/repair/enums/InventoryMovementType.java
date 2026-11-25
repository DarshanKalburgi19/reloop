package com.reloop.repair.enums;

/**
 * Why a spare part's quantity changed. Stored as a string, never as an ordinal.
 *
 * <ul>
 *   <li>{@link #STOCK_IN}: stock received from a supplier (quantity goes up). Also used for the opening stock
 *       entered when a part is first added.</li>
 *   <li>{@link #USED_IN_REPAIR}: fitted during a repair (quantity goes down). The movement is linked to the
 *       repair request.</li>
 *   <li>{@link #ADJUSTMENT}: correction after a stock count, damage, loss or a returned unused part
 *       (either direction). A note is mandatory.</li>
 * </ul>
 */
public enum InventoryMovementType {
    STOCK_IN,
    USED_IN_REPAIR,
    ADJUSTMENT
}
