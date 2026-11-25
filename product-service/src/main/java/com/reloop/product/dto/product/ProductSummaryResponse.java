package com.reloop.product.dto.product;

import com.reloop.product.enums.ProductCategory;
import com.reloop.product.enums.ProductCondition;
import com.reloop.product.enums.ProductStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Compact card used by browse/search results and "my listings". */
public record ProductSummaryResponse(
        Long id,
        Long sellerId,
        String sellerName,
        String title,
        ProductCategory category,
        BigDecimal price,
        long pricePaise,
        String currency,
        ProductCondition condition,
        String city,
        String storeName,
        String pickupLocation,
        String thumbnailUrl,
        ProductStatus availability,
        String passportCode,
        LocalDateTime createdAt) {
}
