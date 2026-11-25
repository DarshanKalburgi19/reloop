package com.reloop.orderpayment.exception;

/**
 * repair-service didn't accept a call. {@code status}: 503 unreachable, 409 refused because of the repair
 * request's state, 502 any other error.
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
