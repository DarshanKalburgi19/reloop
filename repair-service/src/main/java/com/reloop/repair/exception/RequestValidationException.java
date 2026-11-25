package com.reloop.repair.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Cross-field validation failures (field path -> message). Mapped to 400 with {@code fieldErrors}. */
public class RequestValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public RequestValidationException(Map<String, String> fieldErrors) {
        super("Request validation failed");
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
