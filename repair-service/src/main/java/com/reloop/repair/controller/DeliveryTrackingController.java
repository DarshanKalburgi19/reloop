package com.reloop.repair.controller;

import com.reloop.repair.dto.tracking.CreateDeliveryRequest;
import com.reloop.repair.dto.tracking.DeliveryResponse;
import com.reloop.repair.dto.tracking.DeliverySummaryResponse;
import com.reloop.repair.dto.tracking.DemoLocationResponse;
import com.reloop.repair.dto.tracking.TrackingPointResponse;
import com.reloop.repair.security.AuthenticatedAccount;
import com.reloop.repair.service.DeliveryTrackingService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SIMULATED delivery tracking API (see docs/DELIVERY_TRACKING.md). Every response is labelled
 * {@code "simulated": true}; there is no GPS or courier integration.
 *
 * <pre>
 * GET  /api/tracking/demo-locations                          public; the predefined Gujarat demo points
 * POST /api/tracking/deliveries                              book a leg                       201 DeliveryResponse
 * GET  /api/tracking/deliveries/mine                         legs where I am a party
 * GET  /api/tracking/deliveries/{trackingNumber}             full tracking view (+ history)  both parties, same data
 * GET  /api/tracking/deliveries/{trackingNumber}/history     coordinate history only
 * POST /api/tracking/deliveries/{trackingNumber}/advance     DEMO: next checkpoint            409 once DELIVERED
 * GET  /api/tracking/repair-requests/{id}/deliveries         legs of a repair request (participants only)
 * GET  /api/tracking/orders/{orderId}/deliveries             legs of a marketplace order I am a party of
 * </pre>
 */
@RestController
@RequestMapping(path = "/api/tracking", produces = MediaType.APPLICATION_JSON_VALUE)
public class DeliveryTrackingController {

    private final DeliveryTrackingService service;

    public DeliveryTrackingController(DeliveryTrackingService service) {
        this.service = service;
    }

    @GetMapping("/demo-locations")
    public List<DemoLocationResponse> demoLocations() {
        return service.demoLocations();
    }

    @PostMapping(path = "/deliveries", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DeliveryResponse> book(@AuthenticationPrincipal AuthenticatedAccount principal,
                                                 @Valid @RequestBody CreateDeliveryRequest request) {
        DeliveryResponse created = service.book(principal, request);
        return ResponseEntity.created(URI.create("/api/tracking/deliveries/" + created.trackingNumber())).body(created);
    }

    @GetMapping("/deliveries/mine")
    public List<DeliverySummaryResponse> mine(@AuthenticationPrincipal AuthenticatedAccount principal) {
        return service.mine(principal);
    }

    @GetMapping("/deliveries/{trackingNumber}")
    public DeliveryResponse get(@AuthenticationPrincipal AuthenticatedAccount principal,
                                @PathVariable String trackingNumber) {
        return service.get(principal, trackingNumber);
    }

    @GetMapping("/deliveries/{trackingNumber}/history")
    public List<TrackingPointResponse> history(@AuthenticationPrincipal AuthenticatedAccount principal,
                                               @PathVariable String trackingNumber) {
        return service.history(principal, trackingNumber);
    }

    @PostMapping("/deliveries/{trackingNumber}/advance")
    public DeliveryResponse advance(@AuthenticationPrincipal AuthenticatedAccount principal,
                                    @PathVariable String trackingNumber) {
        return service.advance(principal, trackingNumber);
    }

    @GetMapping("/repair-requests/{id:\\d+}/deliveries")
    public List<DeliverySummaryResponse> forRepairRequest(@AuthenticationPrincipal AuthenticatedAccount principal,
                                                          @PathVariable Long id) {
        return service.forRepairRequest(principal, id);
    }

    @GetMapping("/orders/{orderId:\\d+}/deliveries")
    public List<DeliverySummaryResponse> forOrder(@AuthenticationPrincipal AuthenticatedAccount principal,
                                                  @PathVariable Long orderId) {
        return service.forOrder(principal, orderId);
    }
}
