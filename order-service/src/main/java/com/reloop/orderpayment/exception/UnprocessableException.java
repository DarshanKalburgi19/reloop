package com.reloop.orderpayment.exception;

import java.util.Map;

/**
 * The request is well-formed but its values contradict the server's records, e.g. the amount the client
 * expects to pay differs from the amount owed (422). {@code details} are added to the body.
 */
public class UnprocessableException extends RuntimeException {

    private final Map<String, Object> details;

    public UnprocessableException(String message, Map<String, Object> details) {
        super(message);
        this.details = details;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
