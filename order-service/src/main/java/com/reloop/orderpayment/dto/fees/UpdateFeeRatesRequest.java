package com.reloop.orderpayment.dto.fees;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Body of {@code PUT /api/internal/fee-rates/{type}}: rates as fractions (0.02 = 2%), 0 – 0.5. */
public record UpdateFeeRatesRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("0.5") @Digits(integer = 1, fraction = 6) BigDecimal customerFeeRate,
        @NotNull @DecimalMin("0.0") @DecimalMax("0.5") @Digits(integer = 1, fraction = 6) BigDecimal providerCommissionRate,
        @Size(max = 500) String note) {
}
