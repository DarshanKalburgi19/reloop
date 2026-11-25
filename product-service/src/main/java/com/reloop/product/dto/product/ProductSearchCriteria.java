package com.reloop.product.dto.product;

import com.reloop.product.enums.ProductCategory;
import com.reloop.product.enums.ProductCondition;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Query parameters of {@code GET /api/products}; every one is optional.
 *
 * <pre>
 * q          keyword(s), matched case-insensitively in title OR description; every word must match
 * category   ProductCategory, e.g. ELECTRONICS
 * city       exact city, case-insensitive, e.g. surat
 * condition  ProductCondition, e.g. GOOD
 * minPrice   rupees, inclusive
 * maxPrice   rupees, inclusive
 * sort       newest (default) | oldest | price_asc | price_desc
 * page       0-based (default 0)
 * size       1..50 (default 20)
 * </pre>
 * Only listings with availability ACTIVE are returned.
 */
public record ProductSearchCriteria(
        @Size(max = 100) String q,
        ProductCategory category,
        @Size(max = 120) String city,
        ProductCondition condition,
        @DecimalMin("0") BigDecimal minPrice,
        @DecimalMin("0") BigDecimal maxPrice,
        @Pattern(regexp = ProductValidation.SORT, message = "must be one of newest, oldest, price_asc, price_desc") String sort,
        @Min(0) @Max(10000) Integer page,
        @Min(1) @Max(50) Integer size) {

    public static final int DEFAULT_SIZE = 20;

    /** Cross-field rule: an inverted price range is a client error, not an empty result. */
    @AssertTrue(message = "minPrice must not be greater than maxPrice")
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice.compareTo(maxPrice) <= 0;
    }

    public int pageOrDefault() {
        return page == null ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null ? DEFAULT_SIZE : size;
    }

    public String sortOrDefault() {
        return sort == null || sort.isBlank() ? "newest" : sort;
    }
}
