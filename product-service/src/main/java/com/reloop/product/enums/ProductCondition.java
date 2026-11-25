package com.reloop.product.enums;

/** Physical condition of a second-hand item, declared by the seller. */
public enum ProductCondition {
    /** Barely used, no visible wear. */
    LIKE_NEW,
    /** Light signs of use, fully working. */
    VERY_GOOD,
    /** Visible wear, fully working. */
    GOOD,
    /** Heavy wear or minor faults, works. */
    FAIR,
    /** Does not fully work - a candidate for ReLoop repairers. */
    NEEDS_REPAIR
}
