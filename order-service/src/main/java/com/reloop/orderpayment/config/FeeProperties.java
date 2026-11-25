package com.reloop.orderpayment.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Platform fee rates, from configuration (never hard-coded):
 * <pre>
 * reloop.fees.customer-fee-rate=0.0200        # 2% added on top of the repair price, paid by the customer
 * reloop.fees.repairer-commission-rate=0.0200 # 2% kept from the repairer's price
 * </pre>
 * Rates are copied ("frozen") onto every quote when it is issued, and from the quote onto the payment, so
 * changing them later never alters a quote a customer has already seen.
 */
@Validated
@ConfigurationProperties(prefix = "reloop.fees")
public record FeeProperties(
        @NotNull @DecimalMin("0.0") @DecimalMax("0.5") BigDecimal customerFeeRate,
        @NotNull @DecimalMin("0.0") @DecimalMax("0.5") BigDecimal repairerCommissionRate) {
}
