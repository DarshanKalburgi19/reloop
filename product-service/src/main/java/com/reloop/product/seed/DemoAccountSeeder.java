package com.reloop.product.seed;

import com.reloop.product.dto.auth.CustomerRegistrationRequest;
import com.reloop.product.exception.RepairServiceException;
import com.reloop.product.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Creates the demo CUSTOMER and REPAIRER accounts from {@link DemoAccounts} at startup.
 *
 * <ul>
 *   <li>Only active when {@code reloop.seed.enabled=true} (set in the main, local
 *       application.properties; off in tests and by default).</li>
 *   <li>Idempotent: an account whose e-mail already exists is skipped, so restarts are safe.</li>
 *   <li>Goes through {@link AuthService}, i.e. the same BCrypt hashing and validation-backed
 *       code path as the public registration endpoints.</li>
 *   <li>The repairer needs repair-service (it owns workshop profiles). If repair-service is not
 *       up yet, seeding of the repairer is retried in the background
 *       ({@code reloop.seed.repairer-retry-attempts} x {@code reloop.seed.repairer-retry-delay-ms}).</li>
 * </ul>
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "reloop.seed.enabled", havingValue = "true")
public class DemoAccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountSeeder.class);

    private final AuthService authService;
    private final int retryAttempts;
    private final long retryDelayMs;

    public DemoAccountSeeder(AuthService authService,
                             @Value("${reloop.seed.repairer-retry-attempts:24}") int retryAttempts,
                             @Value("${reloop.seed.repairer-retry-delay-ms:5000}") long retryDelayMs) {
        this.authService = authService;
        this.retryAttempts = retryAttempts;
        this.retryDelayMs = retryDelayMs;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.warn("Demo seeding is ENABLED (reloop.seed.enabled=true). Local development only.");
        seedCustomer();
        if (!trySeedRepairer()) {
            Thread retry = new Thread(this::retrySeedRepairer, "reloop-demo-repairer-seed");
            retry.setDaemon(true);
            retry.start();
        }
    }

    void seedCustomer() {
        for (CustomerRegistrationRequest customer : DemoAccounts.customers()) {
            if (authService.emailExists(customer.email())) {
                log.info("Demo customer already present: {}", customer.email());
                continue;
            }
            authService.registerCustomer(customer);
            log.warn("Seeded TEMPORARY demo CUSTOMER {} (password in docs/AUTH.md)", customer.email());
        }
    }

    /** @return true when the repairer exists afterwards */
    boolean trySeedRepairer() {
        if (authService.emailExists(DemoAccounts.REPAIRER_EMAIL)) {
            log.info("Demo repairer already present: {}", DemoAccounts.REPAIRER_EMAIL);
            return true;
        }
        try {
            authService.registerRepairer(DemoAccounts.repairer());
            log.warn("Seeded TEMPORARY demo REPAIRER {} (password in docs/AUTH.md)", DemoAccounts.REPAIRER_EMAIL);
            return true;
        } catch (RepairServiceException ex) {
            log.warn("Demo repairer not seeded yet: {}", ex.getMessage());
            return false;
        }
    }

    private void retrySeedRepairer() {
        for (int attempt = 1; attempt <= retryAttempts; attempt++) {
            try {
                Thread.sleep(retryDelayMs);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                if (trySeedRepairer()) {
                    return;
                }
            } catch (RuntimeException ex) {
                log.warn("Demo repairer seeding failed: {}", ex.getMessage());
                return;
            }
        }
        log.warn("Gave up seeding the demo repairer. Start repair-service and restart product-service.");
    }
}
