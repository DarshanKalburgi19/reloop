package com.reloop.orderpayment.dto.fees;

import com.reloop.orderpayment.enums.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Fee rates in force for one transaction type.
 *
 * @param source {@code DATABASE} (override row) or {@code APPLICATION_PROPERTIES} (defaults)
 */
public record FeeRateView(
        TransactionType transactionType,
        BigDecimal customerFeeRate,
        BigDecimal providerCommissionRate,
        String source,
        String note,
        LocalDateTime updatedAt) {
}
