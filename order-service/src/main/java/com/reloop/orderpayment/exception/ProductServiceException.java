package com.reloop.orderpayment.exception;

/**
 * product-service didn't accept a call. {@code status} is what order-service answers: 503 unreachable,
 * 404 product not found, 403 own listing, 409 not available / reserved by another order, 502 anything else.
 */
public class ProductServiceException extends RuntimeException {

    private final int status;

    public ProductServiceException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
