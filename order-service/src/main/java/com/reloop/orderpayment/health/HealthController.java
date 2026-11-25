package com.reloop.orderpayment.health;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes {@code GET /api/health} - a simple, stable URL the frontend and the
 * other services can call to check that this service is alive.
 *
 * <p>The controller stays thin: it only delegates to {@link HealthService}.
 * That separation lets us mock the service in slice tests.</p>
 */
@RestController
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return healthService.health();
    }
}
