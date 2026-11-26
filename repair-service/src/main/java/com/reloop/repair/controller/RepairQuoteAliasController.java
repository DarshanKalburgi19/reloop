package com.reloop.repair.controller;

import com.reloop.repair.dto.repairrequest.QuoteRequest;
import com.reloop.repair.dto.repairrequest.RepairRequestResponse;
import com.reloop.repair.dto.repairrequest.StatusNoteRequest;
import com.reloop.repair.security.AuthenticatedAccount;
import com.reloop.repair.service.RepairRequestService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Short routes from the payments specification, same behaviour as the repair-request workflow endpoints:
 *
 * <pre>
 * POST /api/repairs/{id}/quote    REPAIRER  = POST /api/repair-requests/{id}/quote (first quote) or
 *                                             /quote/revise (when the request is already QUOTED / AWAITING_PAYMENT)
 *      {"amount":1000.00,"summary":"Replace screen","estimatedDays":2}
 * POST /api/repairs/{id}/approve  CUSTOMER  = POST /api/repair-requests/{id}/quote/approve
 * </pre>
 * repair-service owns the request workflow; order-service computes the fees and freezes the rates on the quote.
 * After approval the customer pays with order-service {@code POST /api/payments/simulate {"repairQuoteId":…}}.
 */
@RestController
@RequestMapping(path = "/api/repairs", produces = MediaType.APPLICATION_JSON_VALUE)
public class RepairQuoteAliasController {

    private final RepairRequestService service;

    public RepairQuoteAliasController(RepairRequestService service) {
        this.service = service;
    }

    @PostMapping(path = "/{id:\\d+}/quote", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('REPAIRER')")
    public RepairRequestResponse quote(@AuthenticationPrincipal AuthenticatedAccount principal, @PathVariable Long id,
                                       @Valid @RequestBody QuoteRequest body) {
        return service.quoteOrRevise(principal, id, body);
    }

    @PostMapping("/{id:\\d+}/approve")
    @PreAuthorize("hasRole('CUSTOMER')")
    public RepairRequestResponse approve(@AuthenticationPrincipal AuthenticatedAccount principal, @PathVariable Long id,
                                         @Valid @RequestBody(required = false) StatusNoteRequest body) {
        return service.approveQuote(principal, id, body == null ? null : body.note());
    }
}
