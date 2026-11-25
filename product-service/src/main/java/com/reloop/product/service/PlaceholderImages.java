package com.reloop.product.service;

import com.reloop.product.enums.ProductCategory;
import java.util.Locale;

/**
 * Real image hosting is out of scope for this phase. Listings without images get a bundled SVG
 * placeholder per category, served by product-service from
 * {@code src/main/resources/static/demo-images/placeholders/}.
 */
public final class PlaceholderImages {

    public static final String PREFIX = "/demo-images/placeholders/";

    private PlaceholderImages() {
    }

    /** ELECTRONICS -> /demo-images/placeholders/electronics.svg */
    public static String forCategory(ProductCategory category) {
        return PREFIX + category.name().toLowerCase(Locale.ROOT).replace('_', '-') + ".svg";
    }

    public static boolean isPlaceholder(String url) {
        return url != null && url.startsWith(PREFIX);
    }
}
