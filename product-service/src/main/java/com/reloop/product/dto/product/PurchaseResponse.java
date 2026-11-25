package com.reloop.product.dto.product;

import com.reloop.product.enums.ProductStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Result of {@code POST /api/products/{id}/purchase}. The ownership of the item's passport
 * moves to the buyer. Payment is out of scope for this phase (see docs/MARKETPLACE.md).
 */
public record PurchaseResponse(
        Long productId,
        String title,
        String passportCode,
        Long sellerId,
        Long buyerId,
        BigDecimal price,
        long pricePaise,
        String currency,
        ProductStatus availability,
        LocalDateTime purchasedAt,
        String note) {
}
