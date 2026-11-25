package com.reloop.product.dto.auth;

import java.time.Instant;

/**
 * Returned by register and login. Contains the access token and the public account view -
 * never a password or password hash.
 */
public record AuthResponse(
        String tokenType,
        String accessToken,
        Instant expiresAt,
        long expiresInSeconds,
        AccountResponse account) {

    public static AuthResponse bearer(String token, Instant expiresAt, long expiresIn, AccountResponse account) {
        return new AuthResponse("Bearer", token, expiresAt, expiresIn, account);
    }
}
