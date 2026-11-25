package com.reloop.repair.security;

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
 * Reads {@code Authorization: Bearer <jwt>} and authenticates the request when the token is valid.
 * Not a {@code @Component} (it would otherwise also run as a plain servlet filter); it is added to
 * the security chain by {@link com.reloop.repair.config.SecurityConfig}. An invalid token leaves the
 * request anonymous: public endpoints still work, protected ones answer with a JSON 401.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTRIBUTE = "reloop.auth.error";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtVerifier verifier;

    public JwtAuthenticationFilter(JwtVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            Optional<AuthenticatedAccount> principal = verifier.verify(header.substring(BEARER_PREFIX.length()).trim());
            if (principal.isPresent()) {
                AuthenticatedAccount account = principal.get();
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(account, null, account.authorities()));
            } else {
                SecurityContextHolder.clearContext();
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, "Invalid or expired access token");
            }
        }
        chain.doFilter(request, response);
    }
}
