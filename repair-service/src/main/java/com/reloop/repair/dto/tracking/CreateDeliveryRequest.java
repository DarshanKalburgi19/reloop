package com.reloop.repair.dto.tracking;

import com.reloop.repair.enums.DeliveryType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/tracking/deliveries}: book a SIMULATED delivery leg.
 *
 * @param deliveryType    STORE_TO_CUSTOMER, CUSTOMER_TO_REPAIRER or REPAIRER_TO_CUSTOMER
 * @param repairRequestId required for the two repair legs, must be absent for STORE_TO_CUSTOMER
 * @param orderId         order-service order id; required for STORE_TO_CUSTOMER, absent otherwise
 * @param originCode      a demo location code ({@code GET /api/tracking/demo-locations}); defaults to the workshop
 *                        for REPAIRER_TO_CUSTOMER
 * @param destinationCode a demo location code; defaults to the workshop for CUSTOMER_TO_REPAIRER and to the
 *                        inbound leg's origin for REPAIRER_TO_CUSTOMER
 */
public record CreateDeliveryRequest(
        @NotNull DeliveryType deliveryType,
        @Positive Long repairRequestId,
        @Positive Long orderId,
        @Size(max = 40) String originCode,
        @Size(max = 40) String destinationCode) {
}
