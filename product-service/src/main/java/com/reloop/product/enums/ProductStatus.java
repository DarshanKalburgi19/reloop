package com.reloop.product.enums;

/**
 * ProductStatus - fixed status/choice enum (stored as string, never ordinal).
 *
 * <p>Marketplace listing lifecycle. RESERVED = in checkout, SOLD = purchase completed.</p>
 */
public enum ProductStatus {

    DRAFT,
    ACTIVE,
    RESERVED,
    SOLD,
    ARCHIVED

}
