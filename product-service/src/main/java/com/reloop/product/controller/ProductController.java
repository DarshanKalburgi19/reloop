package com.reloop.product.controller;

import com.reloop.product.dto.product.CreateProductRequest;
import com.reloop.product.dto.product.PageResponse;
import com.reloop.product.dto.product.PassportResponse;
import com.reloop.product.dto.product.ProductResponse;
import com.reloop.product.dto.product.ProductSearchCriteria;
import com.reloop.product.dto.product.ProductSummaryResponse;
import com.reloop.product.dto.product.PurchaseResponse;
import com.reloop.product.dto.product.UpdateProductRequest;
import com.reloop.product.security.AuthenticatedAccount;
import com.reloop.product.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Second-hand marketplace API.
 *
 * <pre>
 * GET    /api/products                 public    browse ACTIVE listings (search, filters, sort, paging)
 * GET    /api/products/{id}            public    details (deactivated listings: seller only)
 * GET    /api/products/{id}/passport   public    passport code and full history
 * POST   /api/products                 CUSTOMER  create a listing (201 + Location)
 * PUT    /api/products/{id}            CUSTOMER  edit own listing (also deactivate/reactivate via status)
 * DELETE /api/products/{id}            CUSTOMER  deactivate own listing (soft delete, 204)
 * GET    /api/products/mine            CUSTOMER  own listings in every status
 * POST   /api/products/{id}/purchase   CUSTOMER  buy another customer's ACTIVE listing
 * </pre>
 *
 * <p>Role checks are applied twice: URL rules in SecurityConfig and {@code @PreAuthorize} here.
 * Ownership and availability rules live in {@link ProductService}.</p>
 */
@RestController
@RequestMapping(path = "/api/products", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public PageResponse<ProductSummaryResponse> browse(@Valid @ModelAttribute ProductSearchCriteria criteria) {
        return productService.browse(criteria);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public PageResponse<ProductSummaryResponse> mine(@AuthenticationPrincipal AuthenticatedAccount principal,
                                                     @RequestParam(defaultValue = "0") @Min(0) int page,
                                                     @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return productService.mine(principal.accountId(), page, size);
    }

    @GetMapping("/{id:\\d+}")
    public ProductResponse get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedAccount principal) {
        return productService.get(id, viewerId(principal));
    }

    @GetMapping("/{id:\\d+}/passport")
    public PassportResponse passport(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedAccount principal) {
        return productService.passport(id, viewerId(principal));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ProductResponse> create(@AuthenticationPrincipal AuthenticatedAccount principal,
                                                  @Valid @RequestBody CreateProductRequest request) {
        ProductResponse created = productService.create(principal.accountId(), request);
        return ResponseEntity.created(URI.create("/api/products/" + created.id())).body(created);
    }

    @PutMapping(path = "/{id:\\d+}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ProductResponse update(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedAccount principal,
                                  @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(principal.accountId(), id, request);
    }

    @DeleteMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedAccount principal) {
        productService.deactivate(principal.accountId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id:\\d+}/purchase")
    @PreAuthorize("hasRole('CUSTOMER')")
    public PurchaseResponse purchase(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedAccount principal) {
        return productService.purchase(principal.accountId(), id);
    }

    /** Anonymous visitors have no AuthenticatedAccount principal, so the argument is null. */
    private static Long viewerId(AuthenticatedAccount principal) {
        return principal == null ? null : principal.accountId();
    }
}
