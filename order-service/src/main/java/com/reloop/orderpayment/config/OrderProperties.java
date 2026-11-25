package com.reloop.orderpayment.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Marketplace order settings:
 * <pre>
 * reloop.orders.payment-window-minutes=30       # unpaid orders are cancelled (and the product released) after this
 * reloop.orders.expiry-sweep.enabled=true       # background job that cancels expired orders
 * reloop.orders.expiry-sweep.interval-ms=60000
 * </pre>
 */
@Validated
@ConfigurationProperties(prefix = "reloop.orders")
public record OrderProperties(@Min(1) @Max(1440) Integer paymentWindowMinutes) {

    public OrderProperties {
        if (paymentWindowMinutes == null) {
            paymentWindowMinutes = 30;
        }
    }
}
