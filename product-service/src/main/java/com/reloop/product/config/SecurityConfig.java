package com.reloop.product.config;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.withDefaults;

import com.reloop.product.security.JsonErrorWriter;
import com.reloop.product.security.JwtAuthenticationFilter;
import com.reloop.product.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * HTTP security for product-service (the ReLoop identity provider).
 *
 * <ul>
 *   <li><b>Stateless:</b> no HTTP session, no cookies; every request carries a Bearer JWT.
 *       Because no cookie is used for auth, CSRF protection is not applicable and is disabled.</li>
 *   <li><b>Public:</b> registration, login, health, actuator health/info, H2 console (local dev),
 *       marketplace reads ({@code GET /api/products}, {@code /{id}}, {@code /{id}/passport})
 *       and the bundled demo images under {@code /demo-images/**}.</li>
 *   <li><b>Marketplace writes:</b> create/edit/delete/purchase and {@code /api/products/mine}
 *       require ROLE_CUSTOMER (repairers get 403).</li>
 *   <li><b>Role-gated (URL level):</b> {@code /api/customer/**} needs ROLE_CUSTOMER,
 *       {@code /api/repairer/**} needs ROLE_REPAIRER. Controllers repeat this with
 *       {@code @PreAuthorize} (defence in depth via {@link EnableMethodSecurity}).</li>
 *   <li><b>Internal:</b> {@code /api/internal/**} (order-service reserve / confirm-sale / release) bypasses JWT
 *       and is guarded by the shared {@code X-Internal-Api-Key} instead.</li>
 *   <li><b>Everything else</b> (incl. {@code GET /api/auth/me}) requires any valid token.</li>
 *   <li>401/403 responses are JSON, never HTML or a login redirect.</li>
 * </ul>
 *
 * <p>Matchers use {@link PathPatternRequestMatcher} (path within the application), which is
 * safe even though the H2 console registers a second servlet.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        PathPatternRequestMatcher.Builder path = withDefaults();
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // H2 console renders in frames; allow same-origin framing only.
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    Object reason = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
                    JsonErrorWriter.write(response, 401, "Unauthorized",
                            reason != null ? reason.toString() : "Authentication required: send 'Authorization: Bearer <token>'",
                            request.getRequestURI());
                })
                .accessDeniedHandler((request, response, deniedException) ->
                    JsonErrorWriter.write(response, 403, "Forbidden",
                            "Your account role is not allowed to use this endpoint",
                            request.getRequestURI())))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    path.matcher(HttpMethod.POST, "/api/auth/register/customer"),
                    path.matcher(HttpMethod.POST, "/api/auth/register/repairer"),
                    path.matcher(HttpMethod.POST, "/api/auth/login")).permitAll()
                .requestMatchers(
                    path.matcher(HttpMethod.GET, "/api/health"),
                    path.matcher("/actuator/health/**"),
                    path.matcher("/actuator/info"),
                    path.matcher("/h2-console/**"),
                    path.matcher("/error")).permitAll()
                // Service-to-service (order-service): authenticated by X-Internal-Api-Key in the controller.
                .requestMatchers(path.matcher("/api/internal/**")).permitAll()
                // Marketplace: browsing is public, everything that changes data needs a CUSTOMER.
                .requestMatchers(path.matcher(HttpMethod.GET, "/demo-images/**")).permitAll()
                .requestMatchers(path.matcher(HttpMethod.GET, "/api/products/mine")).hasRole("CUSTOMER")
                .requestMatchers(
                    path.matcher(HttpMethod.GET, "/api/products"),
                    path.matcher(HttpMethod.GET, "/api/products/*"),
                    path.matcher(HttpMethod.GET, "/api/products/*/passport")).permitAll()
                .requestMatchers(path.matcher("/api/products/**")).hasRole("CUSTOMER")
                .requestMatchers(path.matcher("/api/customer/**")).hasRole("CUSTOMER")
                .requestMatchers(path.matcher("/api/repairer/**")).hasRole("REPAIRER")
                .anyRequest().authenticated())
            .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * BCrypt with a configurable work factor (default 12 = 2^12 rounds).
     * Tests lower it to 4 via {@code reloop.security.bcrypt-strength} purely for speed.
     */
    @Bean
    public PasswordEncoder passwordEncoder(@Value("${reloop.security.bcrypt-strength:12}") int strength) {
        return new BCryptPasswordEncoder(strength);
    }
}
