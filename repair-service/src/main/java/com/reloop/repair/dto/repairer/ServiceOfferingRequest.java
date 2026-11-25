package com.reloop.repair.dto.repairer;

import com.reloop.repair.enums.PriceType;
import com.reloop.repair.enums.RepairServiceCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * One service with its price, e.g. {@code {"name":"Screen replacement","category":"MOBILE",
 * "priceType":"STARTING_FROM","price":1499}}. Price is in rupees (max 2 decimals).
 *
 * <p>{@code id}: omit for a new service; send the id of an existing service (from the profile
 * response) to update it. Existing services missing from a PUT are deactivated, not deleted.</p>
 */
public record ServiceOfferingRequest(
        @Positive Long id,
        @NotBlank @Size(min = 3, max = 150) String name,
        @Size(max = 500) String description,
        @NotNull RepairServiceCategory category,
        @NotNull PriceType priceType,
        @NotNull @DecimalMin("1.00") @DecimalMax("1000000.00") @Digits(integer = 7, fraction = 2) BigDecimal price,
        @Min(1) @Max(60) Integer estimatedDays) {
}
