package com.reloop.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Product & Account Service (port 8081).
 *
 * <p>This class does two things only:</p>
 * <ul>
 *   <li>{@code @SpringBootApplication} tells Spring Boot to scan the {@code com.reloop.product}
 *       package for components (controllers, services, repositories).</li>
 *   <li>{@code main} starts the embedded web server.</li>
 * </ul>
 */
@SpringBootApplication
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
