package com.reloop.product.exception;

/** The caller tried to change a listing (or relist an item) they don't own. Mapped to 403. */
public class ListingOwnershipException extends RuntimeException {

    public ListingOwnershipException(String message) {
        super(message);
    }
}
