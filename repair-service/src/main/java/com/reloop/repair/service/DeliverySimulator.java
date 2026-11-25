package com.reloop.repair.service;

import com.reloop.repair.entity.Delivery;
import com.reloop.repair.entity.DeliveryLocationPoint;
import com.reloop.repair.enums.DeliveryStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The controlled, deterministic SIMULATION behind delivery tracking. Nothing here reads a GPS device or calls a
 * courier: positions are linear interpolations between the two demo locations, and each demo "advance" moves the
 * delivery to the next fixed checkpoint.
 *
 * <pre>
 * step  status            progress   position
 *  0    BOOKED              0 %      origin
 *  1    PICKED_UP           0 %      origin
 *  2    IN_TRANSIT         20 %      1/5 of the way
 *  3    IN_TRANSIT         40 %
 *  4    IN_TRANSIT         60 %
 *  5    IN_TRANSIT         80 %
 *  6    NEAR_DESTINATION   92 %
 *  7    DELIVERED         100 %      destination
 * </pre>
 *
 * <p>Distance = great-circle distance × {@code reloop.tracking.road-factor} (an estimate of road distance).
 * Estimated arrival = now + remaining distance ÷ {@code reloop.tracking.simulated-speed-kmph}, plus
 * {@code reloop.tracking.pickup-lead-minutes} while still BOOKED. It is recomputed at every checkpoint and stored,
 * so every viewer sees the same value.</p>
 */
@Component
public class DeliverySimulator {

    /** One simulation checkpoint. */
    public record Checkpoint(DeliveryStatus status, int progressPercent) {
    }

    /** A simulated coordinate. */
    public record Position(double latitude, double longitude) {
    }

    public static final List<Checkpoint> CHECKPOINTS = List.of(
            new Checkpoint(DeliveryStatus.BOOKED, 0),
            new Checkpoint(DeliveryStatus.PICKED_UP, 0),
            new Checkpoint(DeliveryStatus.IN_TRANSIT, 20),
            new Checkpoint(DeliveryStatus.IN_TRANSIT, 40),
            new Checkpoint(DeliveryStatus.IN_TRANSIT, 60),
            new Checkpoint(DeliveryStatus.IN_TRANSIT, 80),
            new Checkpoint(DeliveryStatus.NEAR_DESTINATION, 92),
            new Checkpoint(DeliveryStatus.DELIVERED, 100));

    public static final int LAST_STEP = CHECKPOINTS.size() - 1;

    private final double speedKmph;
    private final long pickupLeadMinutes;
    private final double roadFactor;

    public DeliverySimulator(@Value("${reloop.tracking.simulated-speed-kmph:40}") double speedKmph,
                             @Value("${reloop.tracking.pickup-lead-minutes:45}") long pickupLeadMinutes,
                             @Value("${reloop.tracking.road-factor:1.25}") double roadFactor) {
        if (speedKmph <= 0 || pickupLeadMinutes < 0 || roadFactor < 1.0) {
            throw new IllegalArgumentException("reloop.tracking.*: speed must be > 0, pickup lead >= 0, road factor >= 1");
        }
        this.speedKmph = speedKmph;
        this.pickupLeadMinutes = pickupLeadMinutes;
        this.roadFactor = roadFactor;
    }

    /** Estimated road distance between two points, km (one decimal, at least 1.0). */
    public double routeDistanceKm(double fromLat, double fromLng, double toLat, double toLng) {
        return Math.max(1.0, GeoUtils.round1(GeoUtils.distanceKm(fromLat, fromLng, toLat, toLng) * roadFactor));
    }

    /** The point {@code percent}% of the way from origin to destination (linear, 6 decimals). */
    public static Position interpolate(double fromLat, double fromLng, double toLat, double toLng, int percent) {
        double f = Math.max(0, Math.min(100, percent)) / 100.0;
        return new Position(GeoUtils.round6(fromLat + (toLat - fromLat) * f),
                GeoUtils.round6(fromLng + (toLng - fromLng) * f));
    }

    public static double remainingKm(double routeKm, int percent) {
        return GeoUtils.round1(routeKm * (100 - percent) / 100.0);
    }

    /** Estimated arrival when the simulation is at {@code status} with {@code remainingKm} to go, seen at {@code now}. */
    public LocalDateTime estimatedArrival(DeliveryStatus status, double remainingKm, LocalDateTime now) {
        if (status == DeliveryStatus.DELIVERED) {
            return now;
        }
        long travelMinutes = remainingKm <= 0 ? 0 : Math.max(1, (long) Math.ceil(remainingKm / speedKmph * 60.0));
        long lead = status == DeliveryStatus.BOOKED ? pickupLeadMinutes : 0;
        return now.plusMinutes(lead + travelMinutes);
    }

    /** Initialises a new delivery at step 0 (BOOKED, at the origin) and returns the first history point. */
    public DeliveryLocationPoint start(Delivery d, LocalDateTime now, Long accountId) {
        d.setRouteDistanceKm(routeDistanceKm(d.getOriginLatitude(), d.getOriginLongitude(),
                d.getDestinationLatitude(), d.getDestinationLongitude()));
        return moveTo(d, 0, now, accountId);
    }

    /**
     * Moves the delivery to the next checkpoint and returns the new history point.
     *
     * @throws IllegalStateException if it is already DELIVERED (the service turns this into 409 first)
     */
    public DeliveryLocationPoint advance(Delivery d, LocalDateTime now, Long accountId) {
        if (d.getSimulationStep() >= LAST_STEP) {
            throw new IllegalStateException("Delivery " + d.getTrackingNumber() + " is already DELIVERED");
        }
        return moveTo(d, d.getSimulationStep() + 1, now, accountId);
    }

    /** The status the next advance would produce, or null when delivered. */
    public static DeliveryStatus nextStatus(int step) {
        return step >= LAST_STEP ? null : CHECKPOINTS.get(step + 1).status();
    }

    private DeliveryLocationPoint moveTo(Delivery d, int step, LocalDateTime now, Long accountId) {
        Checkpoint c = CHECKPOINTS.get(step);
        Position p = interpolate(d.getOriginLatitude(), d.getOriginLongitude(),
                d.getDestinationLatitude(), d.getDestinationLongitude(), c.progressPercent());
        double remaining = remainingKm(d.getRouteDistanceKm(), c.progressPercent());

        d.setSimulationStep(step);
        d.setStatus(c.status());
        d.setProgressPercent(c.progressPercent());
        d.setCurrentLatitude(p.latitude());
        d.setCurrentLongitude(p.longitude());
        d.setEstimatedArrivalAt(estimatedArrival(c.status(), remaining, now));
        d.setLastAdvancedAt(now);
        if (c.status() == DeliveryStatus.PICKED_UP) {
            d.setPickedUpAt(now);
        }
        if (c.status() == DeliveryStatus.DELIVERED) {
            d.setDeliveredAt(now);
        }
        return new DeliveryLocationPoint(d, step, c.status(), p.latitude(), p.longitude(), c.progressPercent(),
                remaining, note(d, c, remaining), accountId, now);
    }

    private static String note(Delivery d, Checkpoint c, double remaining) {
        return switch (c.status()) {
            case BOOKED -> "Simulated booking created; pickup scheduled at %s, %s."
                    .formatted(d.getOriginLabel(), d.getOriginCity());
            case PICKED_UP -> "Simulated pickup at %s, %s.".formatted(d.getOriginLabel(), d.getOriginCity());
            case IN_TRANSIT -> String.format(Locale.ROOT, "Simulated position: %d%% of the %.1f km route covered, %.1f km to go.",
                    c.progressPercent(), d.getRouteDistanceKm(), remaining);
            case NEAR_DESTINATION -> String.format(Locale.ROOT, "Simulated position: near %s, %s (%.1f km to go).",
                    d.getDestinationLabel(), d.getDestinationCity(), remaining);
            case DELIVERED -> "Simulated delivery completed at %s, %s."
                    .formatted(d.getDestinationLabel(), d.getDestinationCity());
        };
    }
}
