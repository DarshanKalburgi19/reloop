package com.reloop.repair.enums;

/**
 * How a customer can get an item repaired by a workshop. Names match product-service's
 * {@code RepairMethod} used at repairer registration.
 *
 * <ul>
 *   <li>{@link #IN_PERSON} - the customer visits the workshop.</li>
 *   <li>{@link #SEND_ITEM} - the customer sends (ships/couriers) the item to the workshop.</li>
 *   <li>{@link #PICKUP_DROPOFF} - the workshop collects the item and returns it after repair.</li>
 * </ul>
 */
public enum RepairMethod {

    IN_PERSON,
    SEND_ITEM,
    PICKUP_DROPOFF

}
