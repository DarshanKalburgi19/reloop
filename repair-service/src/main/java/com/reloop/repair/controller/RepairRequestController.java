package com.reloop.repair.controller;

import com.reloop.repair.dto.repairer.PageResponse;
import com.reloop.repair.dto.repairrequest.CreateRepairRequest;
import com.reloop.repair.dto.repairrequest.HistoryEntryResponse;
import com.reloop.repair.dto.repairrequest.InspectionRequest;
import com.reloop.repair.dto.repairrequest.QuoteRequest;
import com.reloop.repair.dto.repairrequest.RepairRequestResponse;
import com.reloop.repair.dto.repairrequest.RepairRequestSummaryResponse;
import com.reloop.repair.dto.repairrequest.StatusNoteRequest;
import com.reloop.repair.dto.repairrequest.StatusUpdateRequest;
import com.reloop.repair.enums.RepairStatus;
import com.reloop.repair.security.AuthenticatedAccount;
import com.reloop.repair.service.RepairRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Repair booking workflow (Bearer JWT from product-service).
 *
 * <pre>
 * CUSTOMER
 *   POST /api/repair-requests                     submit (201 + Location)
 *   GET  /api/repair-requests/mine                own request history (?status=...&page=&size=)
 *   POST /api/repair-requests/{id}/quote/approve  approve the current quote -> AWAITING_PAYMENT
 *   POST /api/repair-requests/{id}/quote/reject   reject the current quote  -> QUOTE_REJECTED
 *   (pay: order-service POST /api/payments/simulate {repairQuoteId})
 * REPAIRER (assigned workshop only)
 *   GET  /api/repair-requests/incoming            inbox (?status=...)
 *   POST /api/repair-requests/{id}/accept | /reject | /inspect | /quote | /quote/revise | /progress | /complete
 *   PATCH /api/repair-requests/{id}/status        generic {status, note}
 * BOTH (participants only; others get 404)
 *   GET  /api/repair-requests/{id}                details, quote, allowedActions, timeline
 *   GET  /api/repair-requests/{id}/history        timestamped status history
 *   POST /api/repair-requests/{id}/cancel         {note: reason}, before payment
 * PUBLIC
 *   GET  /api/repair-requests/demo-images         bundled placeholder images to attach
 * </pre>
 */
@RestController
@RequestMapping(path = "/api/repair-requests", produces = MediaType.APPLICATION_JSON_VALUE)
public class RepairRequestController {

    /** Placeholder images bundled with repair-service (no image hosting yet). */
    public static final List<String> DEMO_IMAGES = List.of(
            "/demo-images/repairs/phone-cracked-screen.svg",
            "/demo-images/repairs/laptop-no-power.svg",
            "/demo-images/repairs/appliance-fault.svg",
            "/demo-images/repairs/bicycle-damage.svg",
            "/demo-images/repairs/generic-item.svg");

    private final RepairRequestService service;

    public RepairRequestController(RepairRequestService service) {
        this.service = service;
    }

    /* ─────────────────────────────── customer ─────────────────────────────── */

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<RepairRequestResponse> create(@AuthenticationPrincipal AuthenticatedAccount principal,
                                                        @Valid @RequestBody CreateRepairRequest request) {
        RepairRequestResponse created = service.create(principal, request);
        return ResponseEntity.created(URI.create("/api/repair-requests/" + created.id())).body(created);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public PageResponse<RepairRequestSummaryResponse> mine(@AuthenticationPrincipal AuthenticatedAccount principal,
                                                           @RequestParam(required = false) List<RepairStatus> status,
                                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                                           @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return service.mine(principal, status, page, size);
    }

    @PostMapping("/{id:\\d+}/quote/approve")
    @PreAuthorize("hasRole('CUSTOMER')")
    public RepairRequestResponse approveQuote(@AuthenticationPrincipal AuthenticatedAccount principal, @PathVariable Long id,
                                              @Valid @RequestBody(required = false) StatusNoteRequest body) {
        return service.approveQuote(principal, id, note(body));
    }

    @PostMapping("/{id:\\d+}/quote/reject")
    @PreAuthorize("hasRole('CUSTOMER')")
    public RepairRequestResponse rejectQuote(@AuthenticationPrincipal AuthenticatedAccount principal, @PathVariable Long id,
                                             @Valid @RequestBody(required = false) StatusNoteRequest body) {
        return service.rejectQuote(principal, id, note(body));
    }

    /* ─────────────────────────────── repairer ─────────────────────────────── */

    @GetMapping("/incoming")
    @PreAuthorize("hasRole('REPAIRER')")
    public PageResponse<RepairRequestSummaryResponse> incoming(@AuthenticationPrincipal AuthenticatedAccount principal,
                                                               @RequestParam(required = false) List<RepairStatus> status,
                                                               @RequestParam(defaultValue = "0") @Min(0) int page,
                                                               @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return service.incoming(principal, status, page, size);
    }

    @PostMapping("/{id:\\d+}/accept")
    @PreAuthorize("hasRole('REPAIRER')")
    public RepairRequestResponse accept(@AuthenticationPrincipal AuthenticatedAccount principal, @PathVariable Long id,
                                        @Valid @RequestBody(required = false) StatusNoteRequest body) {
        return service.accept(principal, id, note(body));
    }

    @PostMapping("/{id:\\d+}/reject")
    @PreAuthorize("hasRole('REPAIRER')")
    public RepairRequestResponse reject(@AuthenticationPrincipal AuthenticatedAccount principal, @PathVariable Long id,
                                        @Valid @RequestBody(required = false) StatusNoteRequest body) {
        return service.reject(principal, id, note(body));
    }

    @PostMapping(path = "/{id:\\d+}/inspect", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('REPAIRER')")
    public RepairRequestResponse inspect(@AuthenticationPrincipal AuthenticatedAccount principal, @PathVariable Long id,
                                         @Valid @RequestBody InspectionRequest body) {
        return service.inspect(principal, id, body.findings());
    }

    @PostMapping(path = "/{id:\\d+}/quote", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('REPAIRER')")
    public RepairRequestResponse quote(@AuthenticationPrincipal AuthenticatedAccount principal, @PathVariable Long id,
                                       @Valid @RequestBody QuoteRequest body) {
        return service.quote(principal, id, body, false);
    }

    @PostMapping(path = "/{id:\\d+}/quote/revise", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('REPAIRER')")
    public RepairRequestResponse reviseQuote(@AuthenticationPrincipal AuthenticatedAccount principal, @PathVariable Long id,
                                             @Valid @RequestBody QuoteRequest body) {
        return service.quote(principal, id, body, true);
    }

    @PostMapping("/{id:\\d+}/progress")
    @PreAuthorize("hasRole('REPAIRER')")
    public RepairRequestResponse progress(@AuthenticationPrincipal AuthenticatedAccount principal, @PathVariable Long id,
                                          @Valid @RequestBody(required = false) StatusNoteRequest body) {
        return service.progress(principal, id, note(body));
    }

    @PostMapping("/{id:\\d+}/complete")
    @PreAuthorize("hasRole('REPAIRER')")
    public RepairRequestResponse complete(@AuthenticationPrincipal AuthenticatedAccount principal, @PathVariable Long id,
                                          @Valid @RequestBody(required = false) StatusNoteRequest body) {
        return service.complete(principal, id, note(body));
    }

    @PatchMapping(path = "/{id:\\d+}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('REPAIRER')")
    public RepairRequestResponse updateStatus(@AuthenticationPrincipal AuthenticatedAccount principal, @PathVariable Long id,
                                              @Valid @RequestBody StatusUpdateRequest body) {
        return service.updateStatus(principal, id, body);
    }

    /* ─────────────────────────────── both ─────────────────────────────── */

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'REPAIRER')")
    public RepairRequestResponse get(@AuthenticationPrincipal AuthenticatedAccount principal, @PathVariable Long id) {
        return service.get(principal, id);
    }

    @GetMapping("/{id:\\d+}/history")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'REPAIRER')")
    public List<HistoryEntryResponse> history(@AuthenticationPrincipal AuthenticatedAccount principal,
                                              @PathVariable Long id) {
        return service.history(principal, id);
    }

    @PostMapping("/{id:\\d+}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'REPAIRER')")
    public RepairRequestResponse cancel(@AuthenticationPrincipal AuthenticatedAccount principal, @PathVariable Long id,
                                        @Valid @RequestBody(required = false) StatusNoteRequest body) {
        return service.cancel(principal, id, note(body));
    }

    @GetMapping("/demo-images")
    public List<String> demoImages() {
        return DEMO_IMAGES;
    }

    private static String note(StatusNoteRequest body) {
        return body == null ? null : body.note();
    }
}
