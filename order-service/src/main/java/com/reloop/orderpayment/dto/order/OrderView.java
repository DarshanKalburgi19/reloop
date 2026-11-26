package com.reloop.orderpayment.dto.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A marketplace order with its frozen fee breakdown:
 * {@code totalPaise = subtotalPaise + customerFeePaise} (what the buyer pays),
 * {@code sellerNetPaise = subtotalPaise − providerCommissionPaise},
 * {@code platformRevenuePaise = customerFeePaise + providerCommissionPaise}.
 *
 * @param amountDuePaise total still to pay (0 unless PENDING_PAYMENT); send it as {@code amountPaise} when paying
 * @param payments       every payment attempt, newest last
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderView(
        Long id,
        String orderNumber,
        String transactionType,
        String status,
        Long buyerAccountId,
        Long sellerAccountId,
        Long productId,
        String productTitle,
        List<Item> items,
        long subtotalPaise,
        BigDecimal customerFeeRate,
        long customerFeePaise,
        long totalPaise,
        BigDecimal providerCommissionRate,
        long providerCommissionPaise,
        long sellerNetPaise,
        long platformRevenuePaise,
        long refundedAmountPaise,
        long amountDuePaise,
        String currency,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        LocalDateTime paidAt,
        LocalDateTime cancelledAt,
        String cancelReason,
        String lastPaymentFailure,
        List<PaymentAttempt> payments) {

    public record Item(Long productId, String title, long unitPricePaise, int quantity, long lineTotalPaise) {
    }

    public record PaymentAttempt(String transactionId, String status, String method, long amountPaise,
                                 String failureReason, LocalDateTime attemptedAt) {
    }
}
