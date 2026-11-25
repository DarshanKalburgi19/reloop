package com.reloop.repair.enums;

/**
 * DeliveryTrackingStatus - fixed status/choice enum (stored as string, never ordinal).
 *
 * <p>Shipment progress for sent-in repairs.</p>
 */
public enum DeliveryTrackingStatus {

    CREATED,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    FAILED

}
