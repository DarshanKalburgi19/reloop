package com.reloop.product.exception;

/**
 * repair-service could not create/read a repairer profile.
 * {@code status} is 503 when it is unreachable and 502 when it answered with an error.
 */
public class RepairServiceException extends RuntimeException {

    private final int status;

    public RepairServiceException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
