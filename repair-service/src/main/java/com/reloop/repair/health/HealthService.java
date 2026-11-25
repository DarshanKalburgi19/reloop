package com.reloop.repair.health;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Builds the health payload for this service.
 *
 * <p>Keeping this logic in a separate service class makes it easy to unit-test
 * with Mockito and to extend later (for example, adding database status).</p>
 */
@Service
public class HealthService {

    private final String serviceName;

    /**
     * @param serviceName injected from {@code spring.application.name} in application.properties
     */
    public HealthService(@Value("${spring.application.name}") String serviceName) {
        this.serviceName = serviceName;
    }

    /** Returns a stable, JSON-friendly map: service name + status. */
    public Map<String, String> health() {
        Map<String, String> status = new LinkedHashMap<>();
        status.put("service", serviceName);
        status.put("status", "UP");
        return status;
    }
}
