package com.reloop.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * Entry point of the Product & Account Service (port 8081).
 *
 * <p>This class does two things only:</p>
 * <ul>
 *   <li>{@code @SpringBootApplication} tells Spring Boot to scan the {@code com.reloop.product}
 *       package for components (controllers, services, repositories).</li>
 *   <li>{@code main} starts the embedded web server.</li>
 * </ul>
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} is excluded: ReLoop authenticates with its own
 * accounts table and JWTs, so Spring Boot's generated in-memory "user" (and its logged random
 * password) must not exist.</p>
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
