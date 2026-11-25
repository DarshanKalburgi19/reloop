package com.reloop.product.enums;

/** Events recorded on a product passport. The history is append-only. */
public enum ProductHistoryEventType {
    /** First listing of a physical item: the passport is created. */
    LISTED,
    /** An item bought on ReLoop is listed again by its new owner (same passport). */
    RELISTED,
    /** Title, description, category, condition, location, images or store changed. */
    UPDATED,
    /** Price changed (old and new price recorded). */
    PRICE_CHANGED,
    /** The seller deactivated or deleted the listing. */
    DEACTIVATED,
    /** The seller made a deactivated listing available again. */
    REACTIVATED,
    /** Bought by another customer: ownership of the passport moves to the buyer. */
    SOLD
}
