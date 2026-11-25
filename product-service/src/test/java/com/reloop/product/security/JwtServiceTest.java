package com.reloop.product.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reloop.product.enums.AccountRole;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** Plain unit tests (no Spring) for token issuing and verification. */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-0123456789";

    private final JwtService jwt = new JwtService(SECRET, 3600, "reloop-product-service", Clock.systemUTC());

    @Test
    void issuedTokenVerifiesToTheSameAccount() {
        JwtService.IssuedToken issued = jwt.issue(42L, "a@example.com", AccountRole.REPAIRER);

        AuthenticatedAccount principal = jwt.verify(issued.token()).orElseThrow();
        assertThat(principal.accountId()).isEqualTo(42L);
        assertThat(principal.email()).isEqualTo("a@example.com");
        assertThat(principal.role()).isEqualTo(AccountRole.REPAIRER);
        assertThat(principal.authorities()).extracting(Object::toString).containsExactly("ROLE_REPAIRER");
        assertThat(issued.expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtService other = new JwtService("another-secret-another-secret-0123456789", 3600,
                "reloop-product-service", Clock.systemUTC());
        String foreign = other.issue(1L, "a@example.com", AccountRole.CUSTOMER).token();
        assertThat(jwt.verify(foreign)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        Clock twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        JwtService past = new JwtService(SECRET, 3600, "reloop-product-service", twoHoursAgo);
        String expired = past.issue(1L, "a@example.com", AccountRole.CUSTOMER).token();

        assertThat(past.verify(expired)).as("valid at issue time").isPresent();
        assertThat(jwt.verify(expired)).as("expired one hour ago").isEmpty();
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        JwtService otherIssuer = new JwtService(SECRET, 3600, "someone-else", Clock.systemUTC());
        assertThat(jwt.verify(otherIssuer.issue(1L, "a@example.com", AccountRole.CUSTOMER).token())).isEmpty();
    }

    @Test
    void malformedTokensAreRejected() {
        assertThat(jwt.verify("")).isEmpty();
        assertThat(jwt.verify("abc")).isEmpty();
        assertThat(jwt.verify("a.b.c")).isEmpty();
    }

    @Test
    void tokenCarriesNoPasswordData() {
        String token = jwt.issue(7L, "a@example.com", AccountRole.CUSTOMER).token();
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(payload).contains("\"role\":\"CUSTOMER\"").contains("\"sub\":\"7\"");
        assertThat(payload.toLowerCase()).doesNotContain("password");
    }

    @Test
    void tooShortSecretFailsFast() {
        assertThatThrownBy(() -> new JwtService("short", 3600, "x", Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
