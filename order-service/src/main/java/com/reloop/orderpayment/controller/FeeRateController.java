package com.reloop.orderpayment.controller;

import com.reloop.orderpayment.dto.fees.FeeRateView;
import com.reloop.orderpayment.dto.fees.UpdateFeeRatesRequest;
import com.reloop.orderpayment.enums.TransactionType;
import com.reloop.orderpayment.security.InternalApiKeyGuard;
import com.reloop.orderpayment.service.FeeRateService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fee rates in force.
 *
 * <pre>
 * GET    /api/fee-rates                         any logged-in user: current rates per transaction type + their source
 * PUT    /api/internal/fee-rates/{type}         X-Internal-Api-Key: set a database override
 *                                               {"customerFeeRate":0.025,"providerCommissionRate":0.02,"note":"..."}
 * DELETE /api/internal/fee-rates/{type}         X-Internal-Api-Key: remove the override (back to application.properties)
 * </pre>
 * New rates apply to orders / quotes created afterwards; existing ones keep their frozen rates.
 */
@RestController
public class FeeRateController {

    private final FeeRateService service;
    private final InternalApiKeyGuard guard;

    public FeeRateController(FeeRateService service, InternalApiKeyGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping(path = "/api/fee-rates", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<FeeRateView> current() {
        return service.all();
    }

    @PutMapping(path = "/api/internal/fee-rates/{type}", consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
    public FeeRateView update(@RequestHeader(name = InternalApiKeyGuard.HEADER, required = false) String key,
                              @PathVariable TransactionType type, @Valid @RequestBody UpdateFeeRatesRequest body) {
        guard.verify(key);
        return service.update(type, body);
    }

    @DeleteMapping(path = "/api/internal/fee-rates/{type}", produces = MediaType.APPLICATION_JSON_VALUE)
    public FeeRateView reset(@RequestHeader(name = InternalApiKeyGuard.HEADER, required = false) String key,
                             @PathVariable TransactionType type) {
        guard.verify(key);
        return service.reset(type);
    }
}
