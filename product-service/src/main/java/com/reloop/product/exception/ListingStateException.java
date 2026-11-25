package com.reloop.product.exception;

/** The requested change is not allowed in the listing's current state. Mapped to 409. */
public class ListingStateException extends RuntimeException {

    public ListingStateException(String message) {
        super(message);
    }
}
