package com.reloop.repair.dto.repairer;

import com.reloop.repair.enums.RepairMethod;
import com.reloop.repair.enums.RepairServiceCategory;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Query parameters of {@code GET /api/repairers/nearby}. {@code lat}/{@code lng} use Leaflet's
 * names (e.g. from {@code map.getCenter()} or the browser's geolocation).
 *
 * <pre>
 * lat, lng       required, decimal degrees
 * radiusKm       0.5..100, default 10
 * limit          1..50, default 20 (nearest first)
 * category, minPrice, maxPrice, method, availableOnly   same as GET /api/repairers
 * </pre>
 */
public record NearbySearchCriteria(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lng,
        @DecimalMin("0.5") @DecimalMax("100.0") Double radiusKm,
        RepairServiceCategory category,
        @DecimalMin("0") BigDecimal minPrice,
        @DecimalMin("0") BigDecimal maxPrice,
        RepairMethod method,
        Boolean availableOnly,
        @Min(1) @Max(50) Integer limit) {

    public static final double DEFAULT_RADIUS_KM = 10.0;
    public static final int DEFAULT_LIMIT = 20;

    @AssertTrue(message = "minPrice must not be greater than maxPrice")
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice.compareTo(maxPrice) <= 0;
    }

    public double radiusOrDefault() {
        return radiusKm == null ? DEFAULT_RADIUS_KM : radiusKm;
    }

    public int limitOrDefault() {
        return limit == null ? DEFAULT_LIMIT : limit;
    }
}
