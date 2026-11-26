package com.reloop.repair.enums;

/**
 * The three kinds of SIMULATED delivery leg ReLoop tracks.
 * The two parties of a leg (and the only accounts that can see it) are:
 * <ul>
 *   <li>{@link #STORE_TO_CUSTOMER}: the marketplace seller (partner) and the buyer (customer) of a paid order;</li>
 *   <li>{@link #CUSTOMER_TO_REPAIRER} and {@link #REPAIRER_TO_CUSTOMER}: the repair request's customer and the
 *       repairer who owns the assigned workshop (partner).</li>
 * </ul>
 */
public enum DeliveryType {

    /** Marketplace purchase shipped from the seller/store to the buyer. Linked to an order-service order. */
    STORE_TO_CUSTOMER("Store to customer"),
    /** Item sent in for repair, customer to workshop. Linked to a repair request. */
    CUSTOMER_TO_REPAIRER("Customer to repairer"),
    /** Repaired (or unrepaired) item returned, workshop to customer. Linked to a repair request. */
    REPAIRER_TO_CUSTOMER("Repairer to customer");

    private final String label;

    DeliveryType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isRepairLeg() {
        return this != STORE_TO_CUSTOMER;
    }
}
