package com.reloop.orderpayment.client;

/**
 * Body of {@code POST /api/internal/repair-requests/{id}/payment-events} on repair-service. Must match repair-service's
 * {@code PaymentEventRequest} field for field (checked against {@code contracts/order-to-repair/}).
 *
 * @param eventId              deterministic idempotency key: {@code TXN-…:FAILED}, {@code TXN-…:CANCELLED},
 *                             {@code TXN-…:REFUND:<totalRefundedPaise>}
 * @param type                 FAILED, CANCELLED, PARTIALLY_REFUNDED or REFUNDED
 * @param quoteId              the quote the payment was for
 * @param paymentTransactionId order-service's payment row id
 * @param transactionId        public payment reference (TXN-…)
 * @param amountPaise          amount attempted / charged
 * @param refundedAmountPaise  total refunded on the payment so far (0 for failures)
 * @param reason               failure reason or refund reason
 */
public record RepairPaymentEvent(String eventId, String type, Long quoteId, Long paymentTransactionId,
                                 String transactionId, Long amountPaise, Long refundedAmountPaise, String reason) {

    public static final String FAILED = "FAILED";
    public static final String CANCELLED = "CANCELLED";
    public static final String PARTIALLY_REFUNDED = "PARTIALLY_REFUNDED";
    public static final String REFUNDED = "REFUNDED";
}
