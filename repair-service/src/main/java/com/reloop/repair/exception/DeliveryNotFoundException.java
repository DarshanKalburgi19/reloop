package com.reloop.repair.exception;

/** Unknown tracking number, or a delivery the caller is not a party of (404 either way — no existence leak). */
public class DeliveryNotFoundException extends RuntimeException {

    public DeliveryNotFoundException(String message) {
        super(message);
    }
}
