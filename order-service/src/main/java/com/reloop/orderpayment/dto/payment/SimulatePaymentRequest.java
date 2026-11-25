package com.reloop.orderpayment.dto.payment;

import com.reloop.orderpayment.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Body of {@code POST /api/payments/simulate}. SIMULATED payments only: there are deliberately no fields for
 * card numbers, CVV, UPI PIN or bank credentials, and none may ever be added.
 *
 * @param repairQuoteId the APPROVED quote to pay ({@code quote.quoteId} in the repair request response)
 * @param method        label recorded on the transaction (default UPI)
 * @param outcome       SUCCESS (default) or FAILURE to simulate a declined payment
 */
public record SimulatePaymentRequest(
        @NotNull @Positive Long repairQuoteId,
        PaymentMethod method,
        SimulatedOutcome outcome) {
}
