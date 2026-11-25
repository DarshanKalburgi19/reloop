package com.reloop.repair.dto.repairer;

import java.util.List;

/**
 * {@code GET /api/repairers/nearby}: workshops within {@code radiusKm} of {@code center}, nearest first.
 * {@code bounds} covers the search circle, ready for Leaflet's {@code map.fitBounds}.
 */
public record NearbyResponse(
        LatLng center,
        double radiusKm,
        MapBounds bounds,
        int count,
        List<RepairerSummaryResponse> results) {
}
