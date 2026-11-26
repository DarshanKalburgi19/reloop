package com.reloop.repair.dto.repairrequest;

import com.reloop.repair.enums.PaymentEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/internal/repair-requests/{id}/payment-events} (order-service → repair-service). Must
 * match order-service's {@code RepairPaymentEvent} record field for field (see {@code contracts/}).
 *
 * @param eventId              idempotency key, deterministic on the sender: {@code TXN-…:FAILED},
 *                             {@code TXN-…:CANCELLED} or {@code TXN-…:REFUND:<totalRefundedPaise>}
 * @param type                 FAILED / CANCELLED (an attempt that charged nothing) or PARTIALLY_REFUNDED / REFUNDED
 * @param quoteId              the order-service quote the payment was for
 * @param paymentTransactionId order-service's internal payment row id (informational)
 * @param transactionId        public payment reference (TXN-…), the same id the dashboards and receipts use
 * @param amountPaise          what was attempted / charged
 * @param refundedAmountPaise  everything refunded on this payment so far (refund events; 0 for failures)
 * @param reason               failure reason or refund reason, shown in the timeline
 */
public record PaymentEventRequest(
        @NotBlank @Size(max = 100) String eventId,
        @NotNull PaymentEventType type,
        @NotNull @Positive Long quoteId,
        @Positive Long paymentTransactionId,
        @NotBlank @Size(max = 64) String transactionId,
        @NotNull @PositiveOrZero Long amountPaise,
        @NotNull @PositiveOrZero Long refundedAmountPaise,
        @Size(max = 500) String reason) {
}
