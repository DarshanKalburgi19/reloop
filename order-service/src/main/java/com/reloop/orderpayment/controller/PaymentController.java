package com.reloop.orderpayment.controller;

import com.reloop.orderpayment.dto.payment.PaymentView;
import com.reloop.orderpayment.dto.payment.SimulatePaymentRequest;
import com.reloop.orderpayment.security.AuthenticatedAccount;
import com.reloop.orderpayment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer payments (SIMULATED; never send card, CVV, UPI PIN or bank credentials).
 *
 * <pre>
 * POST /api/payments/simulate         {"repairQuoteId":12,"method":"UPI","outcome":"SUCCESS"}  201 → PaymentView
 * GET  /api/payments/{transactionId}  one of my payments
 * </pre>
 * Both need a CUSTOMER token. Other customers' quotes/payments are 404.
 */
@RestController
@RequestMapping("/api/payments")
@PreAuthorize("hasRole('CUSTOMER')")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping("/simulate")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentView simulate(@AuthenticationPrincipal AuthenticatedAccount caller,
                                @Valid @RequestBody SimulatePaymentRequest body) {
        return service.simulateRepairPayment(caller, body);
    }

    @GetMapping("/{transactionId}")
    public PaymentView get(@AuthenticationPrincipal AuthenticatedAccount caller, @PathVariable String transactionId) {
        return service.get(caller, transactionId);
    }
}
