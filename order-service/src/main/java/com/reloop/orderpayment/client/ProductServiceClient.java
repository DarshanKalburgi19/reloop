package com.reloop.orderpayment.client;

/**
 * order-service → product-service calls for marketplace orders (REST, shared {@code X-Internal-Api-Key}).
 * All three calls are idempotent on product-service for the same {@code orderReference}.
 */
public interface ProductServiceClient {

    /** ACTIVE → RESERVED for this order; returns the price and seller used to build the order. */
    ProductSale reserve(Long productId, Long buyerAccountId, String orderReference);

    /** RESERVED (by this order) → SOLD; passport ownership moves to the buyer. Called after a SUCCESS payment. */
    ProductSale confirmSale(Long productId, Long buyerAccountId, String orderReference, String transactionId);

    /** RESERVED (by this order) → ACTIVE when the order is cancelled or expires. No-op otherwise. */
    ProductSale release(Long productId, String orderReference, String reason);

    /** Records a refund of the sale on the product's passport (idempotent per refund reference). */
    ProductSale recordRefund(Long productId, ProductSaleRefundCommand refund);
}
