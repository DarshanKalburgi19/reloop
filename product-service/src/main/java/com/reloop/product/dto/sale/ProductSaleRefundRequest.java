package com.reloop.product.dto.sale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/internal/products/{id}/sale-refund}, sent by order-service after it refunds a marketplace
 * payment. Must match order-service's {@code ProductSaleRefundCommand} field for field.
 *
 * @param orderReference         the order number the product was sold through (e.g. ORD-2026-1006)
 * @param transactionId          the refunded payment (TXN-…)
 * @param refundReference        the refund just issued (RFD-…); the idempotency key of this call
 * @param refundAmountPaise      what this refund returned to the buyer
 * @param totalRefundedPaise     everything refunded on the payment so far (including this refund)
 * @param fullyRefunded          true when the payment is now REFUNDED (not just PARTIALLY_REFUNDED)
 * @param reason                 refund reason code (CUSTOMER_REQUEST, …)
 */
public record ProductSaleRefundRequest(
        @NotBlank @Size(max = 64) String orderReference,
        @NotBlank @Size(max = 64) String transactionId,
        @NotBlank @Size(max = 64) String refundReference,
        @NotNull @PositiveOrZero Long refundAmountPaise,
        @NotNull @PositiveOrZero Long totalRefundedPaise,
        boolean fullyRefunded,
        @Size(max = 100) String reason) {
}
