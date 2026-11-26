package com.reloop.repair.dto.tracking;

import java.time.LocalDateTime;

/** One saved position of the simulated coordinate history (sequence 0 = booking at the origin). */
public record TrackingPointResponse(
        int sequence,
        String status,
        double latitude,
        double longitude,
        int progressPercent,
        double remainingKm,
        String note,
        LocalDateTime recordedAt) {
}
