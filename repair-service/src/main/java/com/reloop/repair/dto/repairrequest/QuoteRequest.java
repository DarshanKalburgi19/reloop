package com.reloop.repair.dto.repairrequest;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Body of {@code POST /api/repair-requests/{id}/quote} and {@code /quote/revise} (assigned REPAIRER).
 * {@code amount} is the repair price in rupees (max 2 decimals); order-service adds the customer fee.
 */
public record QuoteRequest(
        @NotNull @DecimalMin("1.00") @DecimalMax("1000000.00") @Digits(integer = 7, fraction = 2) BigDecimal amount,
        @NotBlank @Size(min = 10, max = 2000) String summary,
        @Min(1) @Max(60) Integer estimatedDays) {
}
