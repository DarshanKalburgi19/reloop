package com.reloop.product.dto.product;

/** The store a listing is sold from (when applicable). */
public record StoreSummary(Long id, String name, String addressLine, String city) {
}
