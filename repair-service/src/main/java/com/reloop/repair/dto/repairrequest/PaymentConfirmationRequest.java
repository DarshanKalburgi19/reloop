package com.reloop.repair.dto.repairrequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body of the internal {@code POST /api/internal/repair-requests/{id}/payment-confirmation},
 * sent by order-service after a successful (simulated) payment of an approved quote.
 */
public record PaymentConfirmationRequest(
        @NotNull @Positive Long quoteId,
        @NotNull @Positive Long paymentTransactionId,
        @NotBlank @Size(max = 64) String transactionId,
        @NotNull @Positive Long amountPaise) {
}
