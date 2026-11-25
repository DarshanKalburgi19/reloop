package com.reloop.product.exception;

/** No product with that id, or it is a deactivated listing the caller doesn't own. Mapped to 404. */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long id) {
        super("Product " + id + " not found");
    }
}
