package com.reloop.repair.exception;

/** Inventory request that conflicts with current data (duplicate SKU, discontinued part, …). Mapped to 409. */
public class InventoryConflictException extends RuntimeException {

    public InventoryConflictException(String message) {
        super(message);
    }
}
