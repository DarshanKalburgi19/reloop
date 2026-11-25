package com.reloop.repair.dto.inventory;

import com.reloop.repair.entity.SparePart;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/inventory/parts/{id}/use}: parts fitted during a repair. The repair request must be assigned
 * to the caller's workshop and be PAID or IN_PROGRESS.
 */
public record UsePartRequest(
        @NotNull(message = "repairRequestId is required") @Positive Long repairRequestId,
        @NotNull(message = "quantity is required") @Min(value = 1, message = "quantity must be at least 1")
        @Max(SparePart.MAX_QUANTITY) Integer quantity,
        @Size(max = 500) String note) {
}
