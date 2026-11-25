package com.reloop.orderpayment.config;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.withDefaults;

import com.reloop.orderpayment.security.JsonErrorWriter;
import com.reloop.orderpayment.security.JwtAuthenticationFilter;
import com.reloop.orderpayment.security.JwtVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * HTTP security for order-service. Stateless Bearer JWTs issued by product-service; no sessions or cookies.
 *
 * <ul>
 *   <li><b>Public:</b> health, actuator health/info, H2 console (local dev).</li>
 *   <li><b>Internal:</b> {@code /api/internal/**} (called by repair-service) check {@code X-Internal-Api-Key}
 *       in the controllers.</li>
 *   <li><b>Customers:</b> {@code /api/payments/**} (roles via {@code @PreAuthorize}).</li>
 *   <li>Everything else needs a valid token. 401/403 are JSON.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtVerifier verifier) throws Exception {
        PathPatternRequestMatcher.Builder path = withDefaults();
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    Object reason = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
                    JsonErrorWriter.write(response, 401, "Unauthorized",
                            reason != null ? reason.toString()
                                    : "Authentication required: log in at product-service and send 'Authorization: Bearer <token>'",
                            request.getRequestURI());
                })
                .accessDeniedHandler((request, response, deniedException) ->
                    JsonErrorWriter.write(response, 403, "Forbidden",
                            "Your account role is not allowed to use this endpoint", request.getRequestURI())))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    path.matcher(HttpMethod.GET, "/api/health"),
                    path.matcher("/actuator/health/**"),
                    path.matcher("/actuator/info"),
                    path.matcher("/h2-console/**"),
                    path.matcher("/error")).permitAll()
                .requestMatchers(path.matcher("/api/internal/**")).permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(new JwtAuthenticationFilter(verifier), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
