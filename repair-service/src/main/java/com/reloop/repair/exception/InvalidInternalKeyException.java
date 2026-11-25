package com.reloop.repair.exception;

/** Missing or wrong X-Internal-Api-Key on a service-to-service endpoint. */
public class InvalidInternalKeyException extends RuntimeException {

    public InvalidInternalKeyException() {
        super("Missing or invalid X-Internal-Api-Key");
    }
}
