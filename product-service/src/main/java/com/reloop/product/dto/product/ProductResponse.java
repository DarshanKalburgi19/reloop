package com.reloop.product.dto.product;

import com.reloop.product.enums.ProductCategory;
import com.reloop.product.enums.ProductCondition;
import com.reloop.product.enums.ProductStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Full product details ({@code GET /api/products/{id}}, create, update).
 * Seller contact data (email, phone) is intentionally not exposed.
 */
public record ProductResponse(
        Long id,
        Long sellerId,
        String sellerName,
        String title,
        String description,
        ProductCategory category,
        BigDecimal price,
        long pricePaise,
        String currency,
        ProductCondition condition,
        String city,
        StoreSummary store,
        String pickupLocation,
        List<ProductImageResponse> images,
        ProductStatus availability,
        boolean available,
        String passportCode,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime soldAt) {
}
