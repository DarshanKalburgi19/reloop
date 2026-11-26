package com.reloop.orderpayment.controller;

import com.reloop.orderpayment.dto.order.CancelOrderRequest;
import com.reloop.orderpayment.dto.order.CreateOrderRequest;
import com.reloop.orderpayment.dto.order.OrderView;
import com.reloop.orderpayment.security.AuthenticatedAccount;
import com.reloop.orderpayment.service.OrderService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Marketplace orders (transaction type MARKETPLACE_PURCHASE). CUSTOMER role (sellers are customers too).
 *
 * <pre>
 * POST /api/orders              {"productId":42}  201 + Location; 200 if the buyer already has an open order for it
 *                               403 own listing · 404 unknown product · 409 not available / reserved by someone else
 * GET  /api/orders/{id}         buyer or seller of the order (404 for anybody else)
 * GET  /api/orders?role=buyer   my purchases (default) · role=seller: orders for my listings
 * POST /api/orders/{id}/cancel  buyer cancels an unpaid order; the product is released
 * </pre>
 */
@RestController
@RequestMapping(path = "/api/orders", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('CUSTOMER')")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderView> create(@AuthenticationPrincipal AuthenticatedAccount caller,
                                            @Valid @RequestBody CreateOrderRequest body) {
        OrderService.Created created = service.create(caller, body.productId());
        if (created.existing()) {
            return ResponseEntity.ok(created.order());
        }
        return ResponseEntity.created(URI.create("/api/orders/" + created.order().id())).body(created.order());
    }

    @GetMapping("/{id:\\d+}")
    public OrderView get(@AuthenticationPrincipal AuthenticatedAccount caller, @PathVariable Long id) {
        return service.get(caller, id);
    }

    @GetMapping
    public List<OrderView> list(@AuthenticationPrincipal AuthenticatedAccount caller,
                                @RequestParam(defaultValue = "buyer") String role) {
        return service.list(caller, role);
    }

    @PostMapping("/{id:\\d+}/cancel")
    public OrderView cancel(@AuthenticationPrincipal AuthenticatedAccount caller, @PathVariable Long id,
                            @Valid @RequestBody(required = false) CancelOrderRequest body) {
        return service.cancel(caller, id, body == null ? null : body.reason());
    }
}
