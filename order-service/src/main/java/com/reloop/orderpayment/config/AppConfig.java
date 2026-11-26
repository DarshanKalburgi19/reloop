package com.reloop.orderpayment.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Typed configuration and a {@link Clock} (replaceable in tests). */
@Configuration
@EnableConfigurationProperties({FeeProperties.class, OrderProperties.class})
public class AppConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    /** Scheduling is only switched on when the order-expiry sweep is enabled (tests turn it off). */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "reloop.orders.expiry-sweep.enabled", havingValue = "true", matchIfMissing = true)
    static class SchedulingConfig {
    }
}
