package com.reloop.product.exception;

/** A seller tried to buy their own listing. Mapped to 403. */
public class OwnProductPurchaseException extends RuntimeException {

    public OwnProductPurchaseException() {
        super("You cannot purchase your own listing");
    }
}
