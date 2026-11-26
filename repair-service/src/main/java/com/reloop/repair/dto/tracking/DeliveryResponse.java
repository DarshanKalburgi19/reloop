package com.reloop.repair.dto.tracking;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Full SIMULATED tracking view of one delivery leg. Both parties (customer and repairer, or buyer and seller)
 * receive exactly the same document — nothing in it depends on who is asking.
 *
 * @param simulated          always {@code true}
 * @param simulationNotice   human-readable disclaimer to show next to the map
 * @param currentPosition    the simulated marker position
 * @param route              polyline for the map: [[lat, lng] origin, [lat, lng] destination] (straight line)
 * @param statusFlow         all statuses in order, for progress steppers
 * @param nextStatus         what the next demo advance produces; null once DELIVERED
 * @param history            every simulated position, oldest first
 */
public record DeliveryResponse(
        boolean simulated,
        String simulationNotice,
        String trackingNumber,
        String deliveryType,
        String deliveryTypeLabel,
        String carrier,
        String status,
        String statusLabel,
        List<String> statusFlow,
        String nextStatus,
        boolean canAdvance,
        Long repairRequestId,
        Long orderId,
        String referenceNumber,
        Long customerAccountId,
        Long partnerAccountId,
        DeliveryLocationResponse origin,
        DeliveryLocationResponse destination,
        Position currentPosition,
        int progressPercent,
        double routeDistanceKm,
        double remainingDistanceKm,
        LocalDateTime estimatedArrivalAt,
        LocalDateTime bookedAt,
        LocalDateTime pickedUpAt,
        LocalDateTime deliveredAt,
        LocalDateTime lastUpdatedAt,
        List<double[]> route,
        List<TrackingPointResponse> history) {

    /** A simulated coordinate (not GPS). */
    public record Position(double latitude, double longitude) {
    }
}
