package com.reloop.product.dto.product;

/** Shared validation constants for product requests (referenced by docs and tests). */
public final class ProductValidation {

    /**
     * Allowed image URLs while real image hosting is out of scope:
     * a bundled demo image/placeholder under {@code /demo-images/} (no "..") or an external https URL.
     */
    public static final String IMAGE_URL = "^(/demo-images/(?!.*\\.\\.)[A-Za-z0-9._/-]+|https://[^\\s\"'<>]+)$";

    public static final String IMAGE_URL_MESSAGE =
            "must be a /demo-images/... path or an https:// URL (image upload is not available yet)";

    public static final int MAX_IMAGES = 8;

    /** Allowed values of the {@code sort} query parameter. */
    public static final String SORT = "^(newest|oldest|price_asc|price_desc)$";

    private ProductValidation() {
    }
}
