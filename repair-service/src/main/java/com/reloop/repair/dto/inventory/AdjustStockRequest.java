package com.reloop.repair.dto.inventory;

import com.reloop.repair.entity.SparePart;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/inventory/parts/{id}/adjust}: set or correct the available quantity (stock count, damage,
 * loss, unused part returned from a job). Send EXACTLY ONE of:
 * <ul>
 *   <li>{@code newQuantity}: the counted quantity (≥ 0); the difference is recorded</li>
 *   <li>{@code quantityChange}: a signed correction (≠ 0)</li>
 * </ul>
 *
 * @param reason          why (required, stored as the movement note)
 * @param repairRequestId optional link to one of the caller's repair requests (e.g. returning an unused part)
 */
public record AdjustStockRequest(
        @Min(value = 0, message = "newQuantity can't be negative") @Max(SparePart.MAX_QUANTITY) Integer newQuantity,
        @Min(-SparePart.MAX_QUANTITY) @Max(SparePart.MAX_QUANTITY) Integer quantityChange,
        @NotBlank(message = "reason is required for a stock adjustment") @Size(max = 500) String reason,
        @Positive Long repairRequestId) {

    @AssertTrue(message = "send exactly one of newQuantity or quantityChange")
    public boolean isExactlyOneAmount() {
        return (newQuantity == null) != (quantityChange == null);
    }
}
