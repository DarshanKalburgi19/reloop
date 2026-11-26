package com.reloop.repair.controller;

import com.reloop.repair.dto.repairrequest.PaymentConfirmationRequest;
import com.reloop.repair.dto.repairrequest.PaymentEventRequest;
import com.reloop.repair.dto.repairrequest.RepairRequestResponse;
import com.reloop.repair.security.InternalApiKeyGuard;
import com.reloop.repair.service.RepairRequestService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service endpoints (header {@code X-Internal-Api-Key}); never called by browsers.
 *
 * <pre>
 * POST /api/internal/repair-requests/{id}/payment-confirmation   order-service -> repair-service after payment
 *      200: request is PAID (repeats for the same quote are no-ops / adopt the newer TXN reference)
 *      409: not awaiting payment / stale quote / wrong amount
 * POST /api/internal/repair-requests/{id}/payment-events         order-service -> repair-service: FAILED / CANCELLED
 *      attempts and PARTIALLY_REFUNDED / REFUNDED refunds. Idempotent per eventId.
 *      200: recorded (or duplicate)   409: refund for a payment this request wasn't paid with
 * </pre>
 */
@RestController
@RequestMapping(path = "/api/internal/repair-requests", produces = MediaType.APPLICATION_JSON_VALUE)
public class InternalRepairRequestController {

    private final RepairRequestService service;
    private final InternalApiKeyGuard guard;
    private final Validator validator;

    public InternalRepairRequestController(RepairRequestService service, InternalApiKeyGuard guard, Validator validator) {
        this.service = service;
        this.guard = guard;
        this.validator = validator;
    }

    @PostMapping(path = "/{id:\\d+}/payment-confirmation", consumes = MediaType.APPLICATION_JSON_VALUE)
    public RepairRequestResponse confirmPayment(@RequestHeader(name = InternalApiKeyGuard.HEADER, required = false) String key,
                                                @PathVariable Long id,
                                                @RequestBody PaymentConfirmationRequest body) {
        guard.verify(key); // 401 before validation: callers without the key learn nothing
        Set<ConstraintViolation<PaymentConfirmationRequest>> violations = validator.validate(body);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return service.confirmPayment(id, body);
    }

    @PostMapping(path = "/{id:\\d+}/payment-events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public RepairRequestResponse paymentEvent(@RequestHeader(name = InternalApiKeyGuard.HEADER, required = false) String key,
                                              @PathVariable Long id,
                                              @RequestBody PaymentEventRequest body) {
        guard.verify(key);
        Set<ConstraintViolation<PaymentEventRequest>> violations = validator.validate(body);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return service.recordPaymentEvent(id, body);
    }
}
