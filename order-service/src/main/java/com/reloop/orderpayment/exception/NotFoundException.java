package com.reloop.orderpayment.exception;

/** Unknown resource, or one the caller may not see (404). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
