package com.reloop.repair.dto.inventory;

import com.reloop.repair.entity.SparePart;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * {@code POST /api/inventory/parts/{id}/stock-in}: stock received.
 *
 * @param unitCost     purchase price per unit of THIS delivery (rupees). When given, the part's unit cost becomes
 *                     the weighted average of the stock on hand and this delivery; when omitted, the current cost
 *                     is used.
 * @param movementDate when the stock arrived (default now; can't be in the future)
 */
public record StockInRequest(
        @NotNull(message = "quantity is required") @Min(value = 1, message = "quantity must be at least 1")
        @Max(SparePart.MAX_QUANTITY) Integer quantity,
        @DecimalMin(value = "0.00", message = "unitCost can't be negative")
        @Digits(integer = 9, fraction = 2, message = "unitCost must have at most 2 decimals")
        BigDecimal unitCost,
        @Size(max = 500) String note,
        @PastOrPresent(message = "movementDate can't be in the future") LocalDateTime movementDate) {
}
