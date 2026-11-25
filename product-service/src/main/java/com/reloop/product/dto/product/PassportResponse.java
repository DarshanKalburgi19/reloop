package com.reloop.product.dto.product;

import com.reloop.product.enums.ProductCategory;
import com.reloop.product.enums.ProductCondition;
import com.reloop.product.enums.ProductStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code GET /api/products/{id}/passport}: the unique passport identifier of the physical
 * item and its full history across every listing (including earlier owners' listings).
 */
public record PassportResponse(
        String passportCode,
        Long productId,
        String title,
        ProductCategory category,
        ProductCondition condition,
        ProductStatus availability,
        LocalDateTime issuedAt,
        int listingCount,
        int ownershipTransfers,
        List<Long> listingIds,
        List<PassportEventResponse> history) {
}
