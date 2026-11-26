package com.reloop.orderpayment.dto.payment;

import com.reloop.orderpayment.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Body of {@code POST /api/payments/simulate}. SIMULATED payments only: there are deliberately no fields for
 * card numbers, CVV, expiry dates, UPI PINs/IDs or bank credentials, and none may ever be added. Unknown JSON
 * fields are ignored and never stored.
 *
 * <p>Exactly one of {@code orderId} (MARKETPLACE_PURCHASE) or {@code repairQuoteId} (REPAIR_BOOKING) is required.</p>
 *
 * @param orderId       the PENDING_PAYMENT marketplace order to pay
 * @param repairQuoteId the APPROVED repair quote to pay ({@code quote.quoteId} in the repair request response)
 * @param method        UPI, CARD, NET_BANKING or WALLET (a label only)
 * @param outcome       SUCCESS (default), FAILED (simulated decline) or CANCELLED (customer abandoned the payment)
 * @param amountPaise   the total the customer agreed to pay (base + customer fee); must equal the amount owed,
 *                      otherwise 422 and nothing is recorded
 */
public record SimulatePaymentRequest(
        @Positive Long orderId,
        @Positive Long repairQuoteId,
        @NotNull PaymentMethod method,
        SimulatedOutcome outcome,
        @NotNull @Positive Long amountPaise) {

    public SimulatedOutcome outcomeOrDefault() {
        return outcome == null ? SimulatedOutcome.SUCCESS : outcome;
    }
}
