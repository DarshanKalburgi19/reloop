package com.reloop.product.controller;

import com.reloop.product.dto.sale.ProductReservationRequest;
import com.reloop.product.dto.sale.ProductSaleResponse;
import com.reloop.product.security.InternalApiKeyGuard;
import com.reloop.product.service.ProductSaleService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API used by order-service for marketplace orders. Not for browsers: every call needs
 * {@code X-Internal-Api-Key} (401 otherwise).
 *
 * <pre>
 * POST /api/internal/products/{id}/reserve       {"buyerAccountId":7,"orderReference":"ORD-…"}
 * POST /api/internal/products/{id}/confirm-sale  {"buyerAccountId":7,"orderReference":"ORD-…","transactionId":"TXN-…"}
 * POST /api/internal/products/{id}/release       {"orderReference":"ORD-…","reason":"Payment window expired"}
 * </pre>
 */
@RestController
@RequestMapping(path = "/api/internal/products", produces = MediaType.APPLICATION_JSON_VALUE)
public class InternalProductSaleController {

    private final ProductSaleService sales;
    private final InternalApiKeyGuard guard;

    public InternalProductSaleController(ProductSaleService sales, InternalApiKeyGuard guard) {
        this.sales = sales;
        this.guard = guard;
    }

    @PostMapping(path = "/{id:\\d+}/reserve", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ProductSaleResponse reserve(@RequestHeader(name = InternalApiKeyGuard.HEADER, required = false) String key,
                                       @PathVariable Long id, @Valid @RequestBody ProductReservationRequest body) {
        guard.verify(key);
        return sales.reserve(id, body);
    }

    @PostMapping(path = "/{id:\\d+}/confirm-sale", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ProductSaleResponse confirmSale(@RequestHeader(name = InternalApiKeyGuard.HEADER, required = false) String key,
                                           @PathVariable Long id, @Valid @RequestBody ProductReservationRequest body) {
        guard.verify(key);
        return sales.confirmSale(id, body);
    }

    @PostMapping(path = "/{id:\\d+}/release", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ProductSaleResponse release(@RequestHeader(name = InternalApiKeyGuard.HEADER, required = false) String key,
                                       @PathVariable Long id, @Valid @RequestBody ProductReservationRequest body) {
        guard.verify(key);
        return sales.release(id, body);
    }
}
