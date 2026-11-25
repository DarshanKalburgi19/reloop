package com.reloop.product.enums;

/**
 * How a repairer accepts work. Chosen at repairer registration.
 *
 * <p>Stored in repair-service as three boolean columns on {@code repairer_profiles}:</p>
 * <ul>
 *   <li>{@link #IN_PERSON} &rarr; {@code accepts_in_person} (customer visits the workshop)</li>
 *   <li>{@link #SEND_ITEM} &rarr; {@code accepts_shipping} (customer ships/couriers the item)</li>
 *   <li>{@link #PICKUP_DROPOFF} &rarr; {@code accepts_pickup_drop} (repairer collects and returns it)</li>
 * </ul>
 */
public enum RepairMethod {
    IN_PERSON,
    SEND_ITEM,
    PICKUP_DROPOFF
}
