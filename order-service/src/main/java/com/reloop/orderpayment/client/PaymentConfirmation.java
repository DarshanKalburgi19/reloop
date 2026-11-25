package com.reloop.orderpayment.client;

/**
 * Body of repair-service's {@code POST /api/internal/repair-requests/{id}/payment-confirmation}.
 *
 * @param quoteId              the paid quote
 * @param paymentTransactionId database id of the payment (repeat calls with the same id are no-ops there)
 * @param transactionId        human-readable payment reference ({@code PAY-…})
 * @param amountPaise          total charged; must equal the quote's total payable
 */
public record PaymentConfirmation(Long quoteId, Long paymentTransactionId, String transactionId, Long amountPaise) {
}
