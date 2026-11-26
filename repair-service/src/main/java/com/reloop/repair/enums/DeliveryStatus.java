package com.reloop.repair.enums;

/**
 * Status of a SIMULATED delivery. Strictly forward-only; each demo "advance" moves the simulation one
 * checkpoint (see {@code DeliverySimulator}). No courier reports these statuses — ReLoop generates them.
 *
 * <pre>BOOKED → PICKED_UP → IN_TRANSIT → NEAR_DESTINATION → DELIVERED</pre>
 */
public enum DeliveryStatus {

    /** Simulated booking created; parcel still at the origin. */
    BOOKED("Booked"),
    /** Simulated pickup at the origin. */
    PICKED_UP("Picked up"),
    /** Simulated movement along the route. */
    IN_TRANSIT("In transit"),
    /** Simulated position close to the destination. */
    NEAR_DESTINATION("Near destination"),
    /** Simulated hand-over at the destination (terminal). */
    DELIVERED("Delivered");

    private final String label;

    DeliveryStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isTerminal() {
        return this == DELIVERED;
    }
}
