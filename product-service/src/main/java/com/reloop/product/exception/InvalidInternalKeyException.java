package com.reloop.product.exception;

/** Missing or wrong {@code X-Internal-Api-Key} on an internal endpoint. Mapped to 401. */
public class InvalidInternalKeyException extends RuntimeException {

    public InvalidInternalKeyException() {
        super("Missing or invalid X-Internal-Api-Key");
    }
}
