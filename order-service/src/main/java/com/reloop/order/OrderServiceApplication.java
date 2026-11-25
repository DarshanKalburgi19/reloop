package com.reloop.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Order & Payment Service (port 8082).
 *
 * <p>This class does two things only:</p>
 * <ul>
 *   <li>{@code @SpringBootApplication} tells Spring Boot to scan the {@code com.reloop.order}
 *       package for components (controllers, services, repositories).</li>
 *   <li>{@code main} starts the embedded web server.</li>
 * </ul>
 */
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
