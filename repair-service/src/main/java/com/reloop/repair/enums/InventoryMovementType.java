package com.reloop.repair.enums;

/**
 * InventoryMovementType - fixed status/choice enum (stored as string, never ordinal).
 *
 * <p>Why stock changed. RECEIVED adds, CONSUMED subtracts, ADJUSTMENT corrects,
 * RETURNED adds back.</p>
 */
public enum InventoryMovementType {

    RECEIVED,
    CONSUMED,
    ADJUSTMENT,
    RETURNED

}
