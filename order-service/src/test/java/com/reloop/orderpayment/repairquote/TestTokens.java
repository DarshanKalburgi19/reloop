package com.reloop.orderpayment.repairquote;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;

/** Mints JWTs like product-service does, signed with the test secret from test application.properties. */
final class TestTokens {

    static final String SECRET = "test-only-jwt-secret-0123456789-abcdefghijklmnop";
    static final String ISSUER = "reloop-product-service";

    private TestTokens() {
    }

    static String customer(long accountId) {
        return bearer(accountId, "CUSTOMER");
    }

    static String repairer(long accountId) {
        return bearer(accountId, "REPAIRER");
    }

    private static String bearer(long accountId, String role) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder()
                .subject(Long.toString(accountId))
                .issuer(ISSUER)
                .claim("email", "user" + accountId + "@reloop.test")
                .claim("role", role)
                .issuedAt(Date.from(Instant.now().minusSeconds(5)))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(key)
                .compact();
    }
}
