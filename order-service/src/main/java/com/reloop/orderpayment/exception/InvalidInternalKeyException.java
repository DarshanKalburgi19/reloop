package com.reloop.orderpayment.exception;

/** Missing or wrong {@code X-Internal-Api-Key} on a service-to-service endpoint (401). */
public class InvalidInternalKeyException extends RuntimeException {

    public InvalidInternalKeyException() {
        super("Missing or invalid X-Internal-Api-Key");
    }
}
