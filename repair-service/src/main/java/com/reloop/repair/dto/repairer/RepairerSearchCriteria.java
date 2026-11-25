package com.reloop.repair.dto.repairer;

import com.reloop.repair.enums.RepairMethod;
import com.reloop.repair.enums.RepairServiceCategory;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Query parameters of {@code GET /api/repairers}; all optional.
 *
 * <pre>
 * city           exact city, case-insensitive (e.g. surat)
 * category       RepairServiceCategory: the workshop handles this category
 * minPrice       rupees: the workshop has an active service priced >= minPrice (in category, if given)
 * maxPrice       rupees: ... priced <= maxPrice
 * method         RepairMethod: IN_PERSON | SEND_ITEM | PICKUP_DROPOFF
 * availableOnly  true = only workshops with availability AVAILABLE
 * q              keywords in workshop name, description or expertise (every word must match)
 * sort           rating (default) | price_asc | price_desc | name | newest
 * page, size     0-based page (default 0); size 1..50 (default 20)
 * </pre>
 */
public record RepairerSearchCriteria(
        @Size(max = 120) String city,
        RepairServiceCategory category,
        @DecimalMin("0") BigDecimal minPrice,
        @DecimalMin("0") BigDecimal maxPrice,
        RepairMethod method,
        Boolean availableOnly,
        @Size(max = 100) String q,
        @Pattern(regexp = RepairerValidation.SEARCH_SORT,
                message = "must be one of rating, price_asc, price_desc, name, newest") String sort,
        @Min(0) @Max(10000) Integer page,
        @Min(1) @Max(50) Integer size) {

    public static final int DEFAULT_SIZE = 20;

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
        return sort == null || sort.isBlank() ? "rating" : sort;
    }
}
