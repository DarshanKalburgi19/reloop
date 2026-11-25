package com.reloop.repair.exception;

/**
 * order-service could not complete a quote operation.
 * {@code status}: 503 unreachable, 409 refused because of the quote's state, 502 any other error.
 */
public class OrderServiceException extends RuntimeException {

    private final int status;

    public OrderServiceException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
