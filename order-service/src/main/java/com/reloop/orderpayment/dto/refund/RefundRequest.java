package com.reloop.orderpayment.dto.refund;

import com.reloop.orderpayment.enums.RefundReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body of a refund request.
 *
 * @param baseAmountPaise part of the BASE price (item / repair price) to refund; omit for a full refund of what
 *                        remains. The customer fee and the provider commission are reversed in proportion.
 * @param reason          ORDER_CANCELLED, DISPUTED_ORDER, CUSTOMER_REQUEST, ITEM_NOT_AS_DESCRIBED,
 *                        REPAIR_NOT_COMPLETED or OTHER
 * @param note            optional free text (max 500)
 */
public record RefundRequest(
        @Positive Long baseAmountPaise,
        @NotNull RefundReason reason,
        @Size(max = 500) String note) {
}
