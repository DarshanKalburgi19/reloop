package com.reloop.orderpayment.exception;

import java.util.Map;

/** The request conflicts with the current state, e.g. paying a rejected quote (409). {@code details} are added to the body. */
public class ConflictException extends RuntimeException {

    private final Map<String, Object> details;

    public ConflictException(String message) {
        this(message, Map.of());
    }

    public ConflictException(String message, Map<String, Object> details) {
        super(message);
        this.details = details;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
