package com.reloop.repair.dto.inventory;

import com.reloop.repair.enums.SparePartCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * {@code PATCH /api/inventory/parts/{id}}: edit part information. Omitted (null) fields stay unchanged; at least
 * one field is required. The quantity is NOT editable here; use stock-in / use / adjust so every change is
 * recorded as a movement.
 *
 * @param description send "" to clear it
 * @param unitCost    corrects the purchase cost per unit (rupees); doesn't create a movement
 */
public record UpdateSparePartRequest(
        @Size(max = 150) String partName,
        SparePartCategory category,
        @Size(max = 60) @Pattern(regexp = InventoryValidation.SKU_PATTERN, message = InventoryValidation.SKU_MESSAGE)
        String sku,
        @Size(max = 1000) String description,
        @DecimalMin(value = "0.00", message = "unitCost can't be negative")
        @Digits(integer = 9, fraction = 2, message = "unitCost must have at most 2 decimals")
        BigDecimal unitCost,
        @Min(value = 0, message = "lowStockThreshold can't be negative") @Max(InventoryValidation.MAX_THRESHOLD)
        Integer lowStockThreshold) {

    public boolean isEmpty() {
        return partName == null && category == null && sku == null && description == null && unitCost == null
                && lowStockThreshold == null;
    }
}
