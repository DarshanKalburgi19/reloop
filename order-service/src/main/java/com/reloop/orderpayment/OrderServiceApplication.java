package com.reloop.orderpayment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * Entry point of the Order & Payment Service (port 8082).
 *
 * <p>This class does two things only:</p>
 * <ul>
 *   <li>{@code @SpringBootApplication} tells Spring Boot to scan the {@code com.reloop.orderpayment}
 *       package for components (controllers, services, repositories).</li>
 *   <li>{@code main} starts the embedded web server.</li>
 * </ul>
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} is excluded: callers authenticate with JWTs issued by
 * product-service (see {@code SecurityConfig}), so no generated in-memory user/password is wanted.</p>
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
