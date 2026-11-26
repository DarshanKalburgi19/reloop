package com.reloop.orderpayment.client;

/**
 * Body of {@code POST /api/internal/products/{id}/sale-refund} on product-service. Must match product-service's
 * {@code ProductSaleRefundRequest} field for field (checked against {@code contracts/order-to-product/}).
 *
 * @param orderReference     order number the product was sold through
 * @param transactionId      the refunded payment (TXN-…)
 * @param refundReference    the refund just issued (RFD-…); product-service's idempotency key
 * @param refundAmountPaise  what this refund returned to the buyer
 * @param totalRefundedPaise total refunded on the payment so far
 * @param fullyRefunded      payment is now REFUNDED
 * @param reason             refund reason code
 */
public record ProductSaleRefundCommand(String orderReference, String transactionId, String refundReference,
                                       Long refundAmountPaise, Long totalRefundedPaise, boolean fullyRefunded,
                                       String reason) {
}
