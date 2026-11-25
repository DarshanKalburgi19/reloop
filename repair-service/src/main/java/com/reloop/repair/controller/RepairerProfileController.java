package com.reloop.repair.controller;

import com.reloop.repair.dto.CreateRepairerProfileRequest;
import com.reloop.repair.dto.RepairerProfileResponse;
import com.reloop.repair.exception.InvalidInternalKeyException;
import com.reloop.repair.service.RepairerProfileService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Repairer workshop profiles.
 *
 * <pre>
 * POST /api/repairer-profiles       internal: requires X-Internal-Api-Key (called by product-service)
 * GET  /api/repairer-profiles/{id}  public read of workshop details
 * </pre>
 *
 * <p>Creation is internal because a profile must belong to an account registered through
 * product-service ({@code POST /api/auth/register/repairer}); end users never call it directly.</p>
 */
@RestController
@RequestMapping(path = "/api/repairer-profiles", produces = MediaType.APPLICATION_JSON_VALUE)
public class RepairerProfileController {

    public static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";

    private final RepairerProfileService service;
    private final Validator validator;
    private final byte[] internalApiKey;

    public RepairerProfileController(
            RepairerProfileService service,
            Validator validator,
            @Value("${reloop.internal.api-key:reloop-local-dev-internal-key-change-me}") String internalApiKey) {
        this.service = service;
        this.validator = validator;
        this.internalApiKey = internalApiKey.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RepairerProfileResponse> create(
            @RequestHeader(name = INTERNAL_KEY_HEADER, required = false) String key,
            @RequestBody CreateRepairerProfileRequest request) {
        requireInternalKey(key);              // 401 first: unauthenticated callers learn nothing
        Set<ConstraintViolation<CreateRepairerProfileRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/{id}")
    public RepairerProfileResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    private void requireInternalKey(String key) {
        // constant-time comparison
        if (key == null || !MessageDigest.isEqual(internalApiKey, key.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidInternalKeyException();
        }
    }
}
