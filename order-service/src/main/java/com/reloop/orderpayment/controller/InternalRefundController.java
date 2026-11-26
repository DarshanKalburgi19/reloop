package com.reloop.orderpayment.controller;

import com.reloop.orderpayment.dto.payment.TransactionView;
import com.reloop.orderpayment.dto.refund.RefundRequest;
import com.reloop.orderpayment.security.InternalApiKeyGuard;
import com.reloop.orderpayment.service.RefundService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * ReLoop support / dispute refunds (no admin UI yet): {@code POST /api/internal/transactions/{transactionId}/refunds}
 * with {@code X-Internal-Api-Key}. Same body and rules as the provider refund.
 */
@RestController
public class InternalRefundController {

    private final RefundService refunds;
    private final InternalApiKeyGuard guard;

    public InternalRefundController(RefundService refunds, InternalApiKeyGuard guard) {
        this.refunds = refunds;
        this.guard = guard;
    }

    @PostMapping(path = "/api/internal/transactions/{transactionId}/refunds", consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TransactionView> refund(@RequestHeader(name = InternalApiKeyGuard.HEADER, required = false) String key,
                                                  @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                                  @PathVariable String transactionId, @Valid @RequestBody RefundRequest body) {
        guard.verify(key);
        RefundService.Result result = refunds.refundAsPlatform(transactionId, body, idempotencyKey);
        return ResponseEntity.ok().header("Idempotent-Replay", String.valueOf(result.replayed())).body(result.transaction());
    }
}
