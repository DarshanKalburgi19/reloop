package com.reloop.repair.dto.inventory;

/** Shared limits and patterns for inventory requests. */
public final class InventoryValidation {

    /** Letters, digits, '-', '_' and '.', starting with a letter or digit. Stored upper-case. */
    public static final String SKU_PATTERN = "^[A-Za-z0-9][A-Za-z0-9._-]{0,59}$";
    public static final String SKU_MESSAGE =
            "sku may contain letters, digits, '-', '_' and '.', and must start with a letter or digit";
    public static final int MAX_THRESHOLD = 100_000;

    private InventoryValidation() {
    }
}
