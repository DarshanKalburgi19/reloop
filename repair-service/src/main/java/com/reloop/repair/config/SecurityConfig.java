package com.reloop.repair.config;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.withDefaults;

import com.reloop.repair.security.JsonErrorWriter;
import com.reloop.repair.security.JwtAuthenticationFilter;
import com.reloop.repair.security.JwtVerifier;
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
 * HTTP security for repair-service. Stateless: every request carries the Bearer JWT issued by
 * product-service; no session, no cookies, so CSRF protection does not apply.
 *
 * <ul>
 *   <li><b>Public:</b> health, actuator health/info, H2 console (local dev), repairer search
 *       ({@code GET /api/repairers}, {@code /nearby}) and public profiles ({@code GET /api/repairers/{id}}).</li>
 *   <li><b>REPAIRER only:</b> {@code POST /api/repairers}, {@code PUT /api/repairers/{id}},
 *       {@code GET /api/repairers/me}. Ownership (only your own profile) is checked in the service.</li>
 *   <li><b>Internal:</b> {@code /api/repairer-profiles} is open at this level; creation requires the
 *       {@code X-Internal-Api-Key} header, checked in the controller.</li>
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
                // internal service-to-service endpoint (X-Internal-Api-Key checked in the controller)
                .requestMatchers(
                    path.matcher(HttpMethod.POST, "/api/repairer-profiles"),
                    path.matcher(HttpMethod.GET, "/api/repairer-profiles/*")).permitAll()
                // repairer workshop profiles: reads are public, writes need a REPAIRER
                .requestMatchers(path.matcher(HttpMethod.GET, "/api/repairers/me")).hasRole("REPAIRER")
                .requestMatchers(
                    path.matcher(HttpMethod.GET, "/api/repairers"),
                    path.matcher(HttpMethod.GET, "/api/repairers/*")).permitAll()
                .requestMatchers(path.matcher("/api/repairers/**")).hasRole("REPAIRER")
                .anyRequest().authenticated())
            .addFilterBefore(new JwtAuthenticationFilter(verifier), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
