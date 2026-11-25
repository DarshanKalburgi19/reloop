package com.reloop.repair.exception;

/** The caller is a party of the order / repair request but may not perform this delivery action (403). */
public class DeliveryForbiddenException extends RuntimeException {

    public DeliveryForbiddenException(String message) {
        super(message);
    }
}
