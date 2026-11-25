package com.reloop.orderpayment.controller;

import com.reloop.orderpayment.dto.quote.CreateRepairQuoteRequest;
import com.reloop.orderpayment.dto.quote.QuoteDecisionRequest;
import com.reloop.orderpayment.dto.quote.RepairQuoteView;
import com.reloop.orderpayment.security.InternalApiKeyGuard;
import com.reloop.orderpayment.service.RepairQuoteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service quote API used by repair-service. Every call needs {@code X-Internal-Api-Key}
 * (401 otherwise); the key is checked before the body is validated.
 *
 * <pre>
 * POST /api/internal/repair-quotes               issue a quote / revision      201 → RepairQuoteView
 * POST /api/internal/repair-quotes/{id}/approve  customer approved (via repair-service)
 * POST /api/internal/repair-quotes/{id}/reject   customer rejected (via repair-service)
 * GET  /api/internal/repair-quotes/{id}          read a quote
 * </pre>
 */
@RestController
@RequestMapping("/api/internal/repair-quotes")
public class InternalRepairQuoteController {

    private final RepairQuoteService service;
    private final InternalApiKeyGuard guard;

    public InternalRepairQuoteController(RepairQuoteService service, InternalApiKeyGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RepairQuoteView create(@RequestHeader(name = InternalApiKeyGuard.HEADER, required = false) String key,
                                  @Valid @RequestBody CreateRepairQuoteRequest body) {
        guard.verify(key);
        return service.create(body);
    }

    @PostMapping("/{id}/approve")
    public RepairQuoteView approve(@RequestHeader(name = InternalApiKeyGuard.HEADER, required = false) String key,
                                   @PathVariable Long id, @Valid @RequestBody QuoteDecisionRequest body) {
        guard.verify(key);
        return service.approve(id, body);
    }

    @PostMapping("/{id}/reject")
    public RepairQuoteView reject(@RequestHeader(name = InternalApiKeyGuard.HEADER, required = false) String key,
                                  @PathVariable Long id, @Valid @RequestBody QuoteDecisionRequest body) {
        guard.verify(key);
        return service.reject(id, body);
    }

    @GetMapping("/{id}")
    public RepairQuoteView get(@RequestHeader(name = InternalApiKeyGuard.HEADER, required = false) String key,
                               @PathVariable Long id) {
        guard.verify(key);
        return service.get(id);
    }
}
