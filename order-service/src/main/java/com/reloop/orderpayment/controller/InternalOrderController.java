package com.reloop.orderpayment.controller;

import com.reloop.orderpayment.dto.order.InternalOrderView;
import com.reloop.orderpayment.security.InternalApiKeyGuard;
import com.reloop.orderpayment.service.OrderService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service order lookup used by repair-service (simulated delivery tracking).
 * Needs {@code X-Internal-Api-Key} (401 otherwise); unknown order → 404.
 *
 * <pre>
 * GET /api/internal/orders/{id}   → InternalOrderView (no amounts, no payment data)
 * </pre>
 */
@RestController
public class InternalOrderController {

    private final OrderService service;
    private final InternalApiKeyGuard guard;

    public InternalOrderController(OrderService service, InternalApiKeyGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping(path = "/api/internal/orders/{id:\\d+}", produces = MediaType.APPLICATION_JSON_VALUE)
    public InternalOrderView get(@RequestHeader(name = InternalApiKeyGuard.HEADER, required = false) String key,
                                 @PathVariable Long id) {
        guard.verify(key);
        return service.internalView(id);
    }
}
