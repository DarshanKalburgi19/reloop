package com.reloop.product.dto.product;

/** One product image. {@code placeholder} is true for the generated category images. */
public record ProductImageResponse(String url, String altText, boolean primary, boolean placeholder) {
}
