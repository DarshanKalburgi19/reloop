package com.reloop.product.security;

import com.reloop.product.enums.AccountRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies stateless access tokens (JWT, HMAC-SHA256).
 *
 * <p>Claims: {@code sub} = account id, {@code email}, {@code role}, {@code iss}, {@code iat}, {@code exp}.
 * The token carries no password data. Tokens are verified for signature, issuer and expiry.</p>
 *
 * <p>Configuration (application.properties or environment variables):</p>
 * <ul>
 *   <li>{@code reloop.jwt.secret} / {@code RELOOP_JWT_SECRET} - at least 32 bytes. The built-in
 *       default is a <b>development-only</b> value and is logged as a warning.</li>
 *   <li>{@code reloop.jwt.ttl-seconds} - token lifetime, default 3600 (1 hour).</li>
 *   <li>{@code reloop.jwt.issuer} - default {@code reloop-product-service}.</li>
 * </ul>
 */
@Service
public class JwtService {

    /** Development-only signing secret. Override in any shared or deployed environment. */
    public static final String DEV_DEFAULT_SECRET =
            "reloop-local-dev-only-jwt-secret-change-me-0123456789";

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long ttlSeconds;
    private final String issuer;
    private final Clock clock;

    @Autowired
    public JwtService(
            @Value("${reloop.jwt.secret:" + DEV_DEFAULT_SECRET + "}") String secret,
            @Value("${reloop.jwt.ttl-seconds:3600}") long ttlSeconds,
            @Value("${reloop.jwt.issuer:reloop-product-service}") String issuer) {
        this(secret, ttlSeconds, issuer, Clock.systemUTC());
    }

    /** Constructor with an explicit clock - used by unit tests to check expiry. */
    public JwtService(String secret, long ttlSeconds, String issuer, Clock clock) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "reloop.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes for HS256");
        }
        if (ttlSeconds <= 0) {
            throw new IllegalStateException("reloop.jwt.ttl-seconds must be positive");
        }
        if (DEV_DEFAULT_SECRET.equals(secret)) {
            log.warn("Using the built-in DEVELOPMENT JWT secret. Set RELOOP_JWT_SECRET outside local dev.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlSeconds = ttlSeconds;
        this.issuer = issuer;
        this.clock = clock;
    }

    /** Creates a signed token for the given account. */
    public IssuedToken issue(Long accountId, String email, AccountRole role) {
        Instant now = clock.instant();
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        String token = Jwts.builder()
                .subject(String.valueOf(accountId))
                .issuer(issuer)
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, expiresAt, ttlSeconds);
    }

    /**
     * Verifies signature, issuer and expiry and returns the principal.
     * Returns empty for any invalid, tampered, expired or malformed token.
     */
    public Optional<AuthenticatedAccount> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long accountId = Long.valueOf(claims.getSubject());
            String email = claims.get("email", String.class);
            AccountRole role = AccountRole.valueOf(claims.get("role", String.class));
            return Optional.of(new AuthenticatedAccount(accountId, email, role));
        } catch (JwtException | IllegalArgumentException | NullPointerException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /** A freshly issued token plus its expiry. */
    public record IssuedToken(String token, Instant expiresAt, long expiresInSeconds) {
    }
}
