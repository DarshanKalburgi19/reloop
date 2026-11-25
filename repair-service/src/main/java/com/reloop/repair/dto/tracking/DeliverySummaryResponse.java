package com.reloop.repair.dto.tracking;

import java.time.LocalDateTime;

/** List item for "my deliveries" / per-request / per-order lists (no history; fetch the tracking number for it). */
public record DeliverySummaryResponse(
        boolean simulated,
        String trackingNumber,
        String deliveryType,
        String status,
        Long repairRequestId,
        Long orderId,
        String referenceNumber,
        String originLabel,
        String originCity,
        String destinationLabel,
        String destinationCity,
        int progressPercent,
        LocalDateTime estimatedArrivalAt,
        LocalDateTime lastUpdatedAt) {
}
