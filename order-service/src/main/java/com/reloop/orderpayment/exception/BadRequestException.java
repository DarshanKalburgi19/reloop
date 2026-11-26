package com.reloop.orderpayment.exception;

import java.util.Map;

/** A business-level validation error for one field (400 with {@code fieldErrors}). */
public class BadRequestException extends RuntimeException {

    private final String field;

    public BadRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }

    public Map<String, String> fieldErrors() {
        return Map.of(field, getMessage());
    }
}
