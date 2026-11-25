package com.reloop.product.exception;

import com.reloop.product.enums.ProductStatus;

/** The product exists but can't be bought (sold, reserved, deactivated). Mapped to 409. */
public class ProductNotAvailableException extends RuntimeException {

    public ProductNotAvailableException(ProductStatus status) {
        super("Product is not available for purchase (availability: " + status + ")");
    }
}
