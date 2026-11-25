package com.reloop.repair.dto.inventory;

import com.reloop.repair.entity.SparePart;
import com.reloop.repair.enums.SparePartCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * {@code POST /api/inventory/parts}. Money is in rupees with up to 2 decimals (stored as paise).
 *
 * @param sku               optional; generated (e.g. "BAT-7F3K2Q9A") when omitted. Unique per repairer.
 * @param unitCost          purchase cost per unit in rupees
 * @param quantity          opening stock (default 0); recorded as a STOCK_IN movement when above 0
 * @param lowStockThreshold alert when quantity ≤ this (default 5)
 */
public record CreateSparePartRequest(
        @NotBlank(message = "partName is required") @Size(max = 150) String partName,
        @NotNull(message = "category is required") SparePartCategory category,
        @Size(max = 60) @Pattern(regexp = InventoryValidation.SKU_PATTERN, message = InventoryValidation.SKU_MESSAGE)
        String sku,
        @Size(max = 1000) String description,
        @NotNull(message = "unitCost is required")
        @DecimalMin(value = "0.00", message = "unitCost can't be negative")
        @Digits(integer = 9, fraction = 2, message = "unitCost must have at most 2 decimals")
        BigDecimal unitCost,
        @Min(value = 0, message = "quantity can't be negative") @Max(SparePart.MAX_QUANTITY) Integer quantity,
        @Min(value = 0, message = "lowStockThreshold can't be negative") @Max(InventoryValidation.MAX_THRESHOLD)
        Integer lowStockThreshold) {
}
