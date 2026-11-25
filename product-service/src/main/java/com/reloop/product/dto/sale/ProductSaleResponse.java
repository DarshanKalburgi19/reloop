package com.reloop.product.dto.sale;

import com.reloop.product.enums.ProductStatus;

/** What order-service needs about a listing being reserved / sold / released. Price is in paise. */
public record ProductSaleResponse(
        Long productId,
        String title,
        Long sellerAccountId,
        Long buyerAccountId,
        long pricePaise,
        ProductStatus status,
        String passportCode,
        String reservationReference) {
}
