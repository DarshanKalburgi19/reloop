package com.reloop.repair.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Verifies access tokens issued by product-service (JWT, HS256). repair-service never issues
 * tokens; users log in at product-service ({@code POST /api/auth/login}) and send the same
 * {@code Authorization: Bearer <token>} here.
 *
 * <p>{@code reloop.jwt.secret} and {@code reloop.jwt.issuer} must match product-service.
 * Signature, issuer and expiry are checked; any invalid token is simply rejected.</p>
 */
@Service
public class JwtVerifier {

    /** Same development-only default as product-service, so local dev works out of the box. */
    public static final String DEV_DEFAULT_SECRET = "reloop-local-dev-only-jwt-secret-change-me-0123456789";

    private static final Logger log = LoggerFactory.getLogger(JwtVerifier.class);
    private static final int MIN_SECRET_BYTES = 32;
    private static final Pattern ROLE = Pattern.compile("^[A-Z_]{1,30}$");

    private final SecretKey key;
    private final String issuer;

    public JwtVerifier(@Value("${reloop.jwt.secret:" + DEV_DEFAULT_SECRET + "}") String secret,
                       @Value("${reloop.jwt.issuer:reloop-product-service}") String issuer) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("reloop.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes for HS256");
        }
        if (DEV_DEFAULT_SECRET.equals(secret)) {
            log.warn("Using the built-in DEVELOPMENT JWT secret. Set RELOOP_JWT_SECRET outside local dev.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
    }

    /** @return the caller for a valid token; empty for invalid, tampered, expired or malformed tokens */
    public Optional<AuthenticatedAccount> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long accountId = Long.valueOf(claims.getSubject());
            String role = claims.get("role", String.class);
            if (accountId <= 0 || role == null || !ROLE.matcher(role).matches()) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedAccount(accountId, claims.get("email", String.class), role));
        } catch (JwtException | IllegalArgumentException | NullPointerException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
