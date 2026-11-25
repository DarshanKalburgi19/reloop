package com.reloop.orderpayment.controller;

import com.reloop.orderpayment.dto.payment.SimulatePaymentRequest;
import com.reloop.orderpayment.dto.payment.TransactionView;
import com.reloop.orderpayment.security.AuthenticatedAccount;
import com.reloop.orderpayment.service.PaymentService;
import com.reloop.orderpayment.service.TransactionQueryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer payments (SIMULATED; never send card numbers, CVV, UPI PIN or bank credentials: there are no such fields).
 *
 * <pre>
 * POST /api/payments/simulate         CUSTOMER  pay an order or an approved repair quote
 *      {"orderId":5,"method":"UPI","outcome":"SUCCESS","amountPaise":102000}
 *      {"repairQuoteId":12,"method":"CARD","outcome":"FAILED","amountPaise":153000}
 *      optional header Idempotency-Key: a retry with the same key returns the first attempt (200) instead of
 *      creating a new one
 *      201 → TransactionView · 404 not yours · 409 already paid / not payable · 422 incorrect amount
 * GET  /api/payments/{transactionId}  CUSTOMER  alias of GET /api/transactions/{transactionId}
 * </pre>
 */
@RestController
@RequestMapping(path = "/api/payments", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('CUSTOMER')")
public class PaymentController {

    private final PaymentService service;
    private final TransactionQueryService queries;

    public PaymentController(PaymentService service, TransactionQueryService queries) {
        this.service = service;
        this.queries = queries;
    }

    @PostMapping(path = "/simulate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TransactionView> simulate(@AuthenticationPrincipal AuthenticatedAccount caller,
                                                    @RequestHeader(name = "Idempotency-Key", required = false) String key,
                                                    @Valid @RequestBody SimulatePaymentRequest body) {
        PaymentService.Result result = service.simulate(caller, body, key);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotent-Replay", String.valueOf(result.replayed()))
                .body(result.transaction());
    }

    @GetMapping("/{transactionId}")
    public TransactionView get(@AuthenticationPrincipal AuthenticatedAccount caller, @PathVariable String transactionId) {
        return queries.transaction(caller, transactionId);
    }
}
