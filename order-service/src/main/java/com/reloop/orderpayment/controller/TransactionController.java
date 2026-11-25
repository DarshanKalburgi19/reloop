package com.reloop.orderpayment.controller;

import com.reloop.orderpayment.dto.payment.TransactionView;
import com.reloop.orderpayment.dto.refund.RefundRequest;
import com.reloop.orderpayment.dto.report.LedgerEntryView;
import com.reloop.orderpayment.security.AuthenticatedAccount;
import com.reloop.orderpayment.service.ReceiptService;
import com.reloop.orderpayment.service.RefundService;
import com.reloop.orderpayment.service.TransactionQueryService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * One transaction, for its buyer and its provider (seller / repairer). Anybody else gets 404.
 *
 * <pre>
 * GET  /api/transactions/{transactionId}                     the full fee breakdown + refunds
 * GET  /api/transactions/{transactionId}/receipt             printable HTML receipt (print → PDF in the browser)
 * GET  /api/transactions/{transactionId}/receipt?format=text plain-text receipt
 *                                    &amp;download=true          … as a file (Content-Disposition: attachment)
 * GET  /api/transactions/{transactionId}/ledger              simulated ledger lines
 * POST /api/transactions/{transactionId}/refunds             provider only: {"baseAmountPaise":25000,"reason":"CUSTOMER_REQUEST"}
 * </pre>
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionQueryService queries;
    private final ReceiptService receipts;
    private final RefundService refunds;

    public TransactionController(TransactionQueryService queries, ReceiptService receipts, RefundService refunds) {
        this.queries = queries;
        this.receipts = receipts;
        this.refunds = refunds;
    }

    @GetMapping(path = "/{transactionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public TransactionView get(@AuthenticationPrincipal AuthenticatedAccount caller, @PathVariable String transactionId) {
        return queries.transaction(caller, transactionId);
    }

    @GetMapping("/{transactionId}/receipt")
    public ResponseEntity<String> receipt(@AuthenticationPrincipal AuthenticatedAccount caller,
                                          @PathVariable String transactionId,
                                          @RequestParam(defaultValue = "html") String format,
                                          @RequestParam(defaultValue = "false") boolean download) {
        boolean text = "text".equalsIgnoreCase(format) || "txt".equalsIgnoreCase(format);
        String body = text ? receipts.text(caller, transactionId) : receipts.html(caller, transactionId);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(new MediaType(text ? MediaType.TEXT_PLAIN : MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        String filename = "reloop-receipt-" + transactionId + (text ? ".txt" : ".html");
        response.header(HttpHeaders.CONTENT_DISPOSITION,
                (download ? ContentDisposition.attachment() : ContentDisposition.inline()).filename(filename).build().toString());
        return response.body(body);
    }

    @GetMapping(path = "/{transactionId}/ledger", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<LedgerEntryView> ledger(@AuthenticationPrincipal AuthenticatedAccount caller,
                                        @PathVariable String transactionId) {
        return queries.ledger(caller, transactionId);
    }

    @PostMapping(path = "/{transactionId}/refunds", consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public TransactionView refund(@AuthenticationPrincipal AuthenticatedAccount caller, @PathVariable String transactionId,
                                  @Valid @RequestBody RefundRequest body) {
        return refunds.refundAsProvider(caller, transactionId, body);
    }
}
