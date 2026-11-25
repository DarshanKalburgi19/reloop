package com.reloop.repair;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Repair & Tracking Service (port 8083).
 *
 * <p>This class does two things only:</p>
 * <ul>
 *   <li>{@code @SpringBootApplication} tells Spring Boot to scan the {@code com.reloop.repair}
 *       package for components (controllers, services, repositories).</li>
 *   <li>{@code main} starts the embedded web server.</li>
 * </ul>
 */
@SpringBootApplication
public class RepairServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RepairServiceApplication.class, args);
    }
}
