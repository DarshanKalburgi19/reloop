package com.reloop.repair.repairer;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;

/** Mints tokens the way product-service does, signed with the test secret from src/test/resources. */
final class TestTokens {

    static final String SECRET = "test-only-jwt-secret-0123456789-abcdefghijklmnop";
    static final String ISSUER = "reloop-product-service";

    private TestTokens() {
    }

    static String repairer(long accountId) {
        return bearer(mint(accountId, "REPAIRER", SECRET, ISSUER, Instant.now().plusSeconds(3600)));
    }

    static String customer(long accountId) {
        return bearer(mint(accountId, "CUSTOMER", SECRET, ISSUER, Instant.now().plusSeconds(3600)));
    }

    static String bearer(String token) {
        return "Bearer " + token;
    }

    static String mint(long accountId, String role, String secret, String issuer, Instant expiresAt) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(Long.toString(accountId))
                .issuer(issuer)
                .claim("email", "user" + accountId + "@reloop.test")
                .claim("role", role)
                .issuedAt(Date.from(Instant.now().minusSeconds(5)))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }
}
