package com.reloop.repair.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.reloop.repair.entity.Delivery;
import com.reloop.repair.entity.DeliveryLocationPoint;
import com.reloop.repair.enums.DeliveryStatus;
import com.reloop.repair.service.DemoLocations.DemoLocation;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The deterministic simulation: checkpoints, interpolation, distance and estimated arrival. */
class DeliverySimulatorTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 25, 10, 0);

    private final DeliverySimulator simulator = new DeliverySimulator(40, 45, 1.25);

    private static Delivery delivery(String from, String to) {
        DemoLocation o = DemoLocations.find(from).orElseThrow();
        DemoLocation d = DemoLocations.find(to).orElseThrow();
        Delivery x = new Delivery();
        x.setTrackingNumber("TRK-SIM-TEST");
        x.setOriginCode(o.code());
        x.setOriginLabel(o.label());
        x.setOriginCity(o.city());
        x.setOriginLatitude(o.latitude());
        x.setOriginLongitude(o.longitude());
        x.setDestinationCode(d.code());
        x.setDestinationLabel(d.label());
        x.setDestinationCity(d.city());
        x.setDestinationLatitude(d.latitude());
        x.setDestinationLongitude(d.longitude());
        return x;
    }

    @Test
    void checkpointsFollowTheRequiredStatusOrderAndNeverGoBack() {
        List<DeliveryStatus> statuses = DeliverySimulator.CHECKPOINTS.stream().map(DeliverySimulator.Checkpoint::status)
                .distinct().toList();
        assertThat(statuses).containsExactly(DeliveryStatus.BOOKED, DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT,
                DeliveryStatus.NEAR_DESTINATION, DeliveryStatus.DELIVERED);

        int previousProgress = -1;
        int previousOrdinal = -1;
        for (DeliverySimulator.Checkpoint c : DeliverySimulator.CHECKPOINTS) {
            assertThat(c.progressPercent()).isGreaterThanOrEqualTo(previousProgress);
            assertThat(c.status().ordinal()).isGreaterThanOrEqualTo(previousOrdinal);
            previousProgress = c.progressPercent();
            previousOrdinal = c.status().ordinal();
        }
        assertThat(DeliverySimulator.CHECKPOINTS.get(0).progressPercent()).isZero();
        assertThat(DeliverySimulator.CHECKPOINTS.get(DeliverySimulator.LAST_STEP).progressPercent()).isEqualTo(100);
        assertThat(DeliverySimulator.nextStatus(0)).isEqualTo(DeliveryStatus.PICKED_UP);
        assertThat(DeliverySimulator.nextStatus(DeliverySimulator.LAST_STEP)).isNull();
    }

    @Test
    void interpolationIsLinearBetweenTheEnds() {
        DeliverySimulator.Position start = DeliverySimulator.interpolate(22.0, 70.0, 23.0, 72.0, 0);
        DeliverySimulator.Position middle = DeliverySimulator.interpolate(22.0, 70.0, 23.0, 72.0, 50);
        DeliverySimulator.Position end = DeliverySimulator.interpolate(22.0, 70.0, 23.0, 72.0, 100);
        DeliverySimulator.Position clamped = DeliverySimulator.interpolate(22.0, 70.0, 23.0, 72.0, 150);

        assertThat(start).isEqualTo(new DeliverySimulator.Position(22.0, 70.0));
        assertThat(middle).isEqualTo(new DeliverySimulator.Position(22.5, 71.0));
        assertThat(end).isEqualTo(new DeliverySimulator.Position(23.0, 72.0));
        assertThat(clamped).isEqualTo(end);
    }

    @Test
    void routeDistanceIsStraightLineTimesRoadFactor() {
        DemoLocation ahd = DemoLocations.find("AHD_CG_ROAD").orElseThrow();
        DemoLocation surat = DemoLocations.find("SURAT_ADAJAN").orElseThrow();
        double straight = GeoUtils.distanceKm(ahd.latitude(), ahd.longitude(), surat.latitude(), surat.longitude());

        double route = simulator.routeDistanceKm(ahd.latitude(), ahd.longitude(), surat.latitude(), surat.longitude());

        assertThat(straight).isBetween(190.0, 220.0);
        assertThat(route).isCloseTo(straight * 1.25, within(0.06));
        // very close points still get a sensible minimum
        assertThat(simulator.routeDistanceKm(23.0, 72.5, 23.0, 72.5)).isEqualTo(1.0);
    }

    @Test
    void estimatedArrivalUsesSpeedAndPickupLead() {
        // 100 km at 40 km/h = 150 min; BOOKED adds the 45 min pickup lead
        assertThat(simulator.estimatedArrival(DeliveryStatus.BOOKED, 100, T0)).isEqualTo(T0.plusMinutes(195));
        assertThat(simulator.estimatedArrival(DeliveryStatus.IN_TRANSIT, 100, T0)).isEqualTo(T0.plusMinutes(150));
        assertThat(simulator.estimatedArrival(DeliveryStatus.NEAR_DESTINATION, 0.1, T0)).isEqualTo(T0.plusMinutes(1));
        assertThat(simulator.estimatedArrival(DeliveryStatus.DELIVERED, 0, T0)).isEqualTo(T0);
    }

    @Test
    void fullRunMovesTheMarkerTowardTheDestinationAndRecordsEveryPoint() {
        Delivery d = delivery("SURAT_ADAJAN", "AHD_CG_ROAD");
        List<DeliveryLocationPoint> history = new ArrayList<>();
        history.add(simulator.start(d, T0, 7L));

        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.BOOKED);
        assertThat(d.getCurrentLatitude()).isEqualTo(d.getOriginLatitude());
        assertThat(d.getCurrentLongitude()).isEqualTo(d.getOriginLongitude());
        assertThat(d.getEstimatedArrivalAt()).isAfter(T0.plusMinutes(45));

        LocalDateTime now = T0;
        double previousRemaining = Double.MAX_VALUE;
        LocalDateTime previousEta = d.getEstimatedArrivalAt();
        for (int step = 1; step <= DeliverySimulator.LAST_STEP; step++) {
            now = now.plusMinutes(30);
            history.add(simulator.advance(d, now, 7L));
            double remaining = GeoUtils.distanceKm(d.getCurrentLatitude(), d.getCurrentLongitude(),
                    d.getDestinationLatitude(), d.getDestinationLongitude());
            if (step >= 2) {
                assertThat(remaining).as("step %d moves closer", step).isLessThan(previousRemaining);
            }
            previousRemaining = remaining;
            assertThat(d.getEstimatedArrivalAt()).isAfterOrEqualTo(now);
            assertThat(d.getLastAdvancedAt()).isEqualTo(now);
            previousEta = d.getEstimatedArrivalAt();
        }

        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(d.getProgressPercent()).isEqualTo(100);
        assertThat(d.getCurrentLatitude()).isEqualTo(d.getDestinationLatitude());
        assertThat(d.getCurrentLongitude()).isEqualTo(d.getDestinationLongitude());
        assertThat(d.getDeliveredAt()).isEqualTo(now);
        assertThat(previousEta).isEqualTo(now);
        assertThat(d.getPickedUpAt()).isEqualTo(T0.plusMinutes(30));

        assertThat(history).extracting(DeliveryLocationPoint::getSequenceNo).containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
        assertThat(history).extracting(DeliveryLocationPoint::getStatus).containsExactly(DeliveryStatus.BOOKED,
                DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT, DeliveryStatus.IN_TRANSIT, DeliveryStatus.IN_TRANSIT,
                DeliveryStatus.IN_TRANSIT, DeliveryStatus.NEAR_DESTINATION, DeliveryStatus.DELIVERED);
        assertThat(history).allSatisfy(p -> assertThat(p.getNote()).startsWith("Simulated"));
        assertThat(history.get(history.size() - 1).getRemainingKm()).isZero();

        assertThatThrownBy(() -> simulator.advance(d, T0, 7L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsNonsenseConfiguration() {
        assertThatThrownBy(() -> new DeliverySimulator(0, 45, 1.25)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeliverySimulator(40, -1, 1.25)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeliverySimulator(40, 45, 0.9)).isInstanceOf(IllegalArgumentException.class);
    }
}
