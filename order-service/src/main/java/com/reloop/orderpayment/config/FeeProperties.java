package com.reloop.orderpayment.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * DEFAULT platform fee rates, from configuration (never hard-coded):
 * <pre>
 * reloop.fees.customer-fee-rate=0.0200          # 2% added on top of the price, paid by the customer
 * reloop.fees.provider-commission-rate=0.0200   # 2% deducted from the seller's / repairer's gross amount
 * </pre>
 * The older key {@code reloop.fees.repairer-commission-rate} is still honoured: application.properties defines
 * {@code provider-commission-rate=${reloop.fees.repairer-commission-rate:0.0200}}.
 *
 * <p>A row in {@code fee_rate_configs} (see {@link com.reloop.orderpayment.service.FeeRateService}) overrides these
 * defaults for one transaction type. Whatever the source, the rates are copied ("frozen") onto every order and
 * quote when it is created, and from there onto the payment, so changing them never alters an existing
 * transaction.</p>
 */
@Validated
@ConfigurationProperties(prefix = "reloop.fees")
public record FeeProperties(
        @NotNull @DecimalMin("0.0") @DecimalMax("0.5") BigDecimal customerFeeRate,
        @NotNull @DecimalMin("0.0") @DecimalMax("0.5") BigDecimal providerCommissionRate) {

    /** @deprecated use {@link #providerCommissionRate()}; kept for older callers. */
    @Deprecated
    public BigDecimal repairerCommissionRate() {
        return providerCommissionRate;
    }
}
