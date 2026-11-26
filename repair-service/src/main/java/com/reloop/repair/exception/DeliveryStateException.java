package com.reloop.repair.exception;

/**
 * The delivery action conflicts with the current state (409): advancing a DELIVERED delivery, booking a leg the
 * order / repair request is not ready for, or booking the same leg twice. {@code currentStatus} is the status that
 * blocked it (delivery, repair or order status), or null.
 */
public class DeliveryStateException extends RuntimeException {

    private final String currentStatus;

    public DeliveryStateException(String currentStatus, String message) {
        super(message);
        this.currentStatus = currentStatus;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }
}
