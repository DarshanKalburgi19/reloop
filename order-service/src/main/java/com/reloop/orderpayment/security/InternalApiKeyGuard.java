package com.reloop.orderpayment.security;

import com.reloop.orderpayment.exception.InvalidInternalKeyException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Checks the {@value #HEADER} header on service-to-service endpoints ({@code /api/internal/**}).
 * The key ({@code reloop.internal.api-key}, env {@code RELOOP_INTERNAL_API_KEY}) is shared by all ReLoop services.
 */
@Component
public class InternalApiKeyGuard {

    public static final String HEADER = "X-Internal-Api-Key";

    private final byte[] key;

    public InternalApiKeyGuard(@Value("${reloop.internal.api-key:reloop-local-dev-internal-key-change-me}") String key) {
        this.key = key.getBytes(StandardCharsets.UTF_8);
    }

    /** Constant-time comparison; 401 when missing or wrong. */
    public void verify(String presented) {
        if (presented == null || !MessageDigest.isEqual(key, presented.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidInternalKeyException();
        }
    }
}
