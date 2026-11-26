package com.reloop.orderpayment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every {@code reloop.orders.expiry-sweep.interval-ms} (default 60 s): cancels unpaid orders past their payment
 * window and releases their product reservations (retrying releases that failed while product-service was down).
 * Disabled with {@code reloop.orders.expiry-sweep.enabled=false} (tests call {@link OrderService#expireDueOrders()}
 * directly).
 */
@Component
@ConditionalOnProperty(name = "reloop.orders.expiry-sweep.enabled", havingValue = "true", matchIfMissing = true)
public class OrderExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryJob.class);

    private final OrderService orders;

    public OrderExpiryJob(OrderService orders) {
        this.orders = orders;
    }

    @Scheduled(fixedDelayString = "${reloop.orders.expiry-sweep.interval-ms:60000}",
               initialDelayString = "${reloop.orders.expiry-sweep.interval-ms:60000}")
    public void sweep() {
        try {
            int n = orders.expireDueOrders();
            if (n > 0) {
                log.info("Cancelled {} expired unpaid order(s)", n);
            }
        } catch (RuntimeException ex) {
            log.warn("Order expiry sweep failed: {}", ex.getMessage());
        }
    }
}
