package com.reloop.product.exception;

/** A validation rule that cannot be expressed with annotations (e.g. BCrypt's 72-byte limit). */
public class FieldValidationException extends RuntimeException {

    private final String field;

    public FieldValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
