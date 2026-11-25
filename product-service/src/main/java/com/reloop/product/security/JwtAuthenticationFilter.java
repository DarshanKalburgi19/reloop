package com.reloop.product.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads {@code Authorization: Bearer <jwt>}, verifies it and, if valid, authenticates the request.
 *
 * <p>Deliberately <b>not</b> a Spring {@code @Component}: it is created inside
 * {@link com.reloop.product.config.SecurityConfig} and added only to the security filter chain.
 * (A {@code Filter} bean would also be auto-registered as a plain servlet filter and run twice.)</p>
 *
 * <p>An invalid token never short-circuits the request here; it simply leaves the request
 * unauthenticated, so public endpoints keep working and protected ones get a JSON 401.</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Request attribute read by the 401 entry point to explain why authentication failed. */
    public static final String AUTH_ERROR_ATTRIBUTE = "reloop.auth.error";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            Optional<AuthenticatedAccount> principal = jwtService.verify(token);
            if (principal.isPresent()) {
                AuthenticatedAccount account = principal.get();
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(account, null, account.authorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                SecurityContextHolder.clearContext();
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, "Invalid or expired access token");
            }
        }
        chain.doFilter(request, response);
    }
}
