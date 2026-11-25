package com.reloop.repair.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reloop.repair.entity.Delivery;
import com.reloop.repair.entity.DeliveryLocationPoint;
import com.reloop.repair.enums.DeliveryStatus;
import com.reloop.repair.enums.DeliveryType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * The simulated-delivery tables on H2: unique tracking numbers, one leg per type per order / repair request,
 * an append-only history with unique sequence numbers, and the party query. Each failing test ends on its
 * single expected violation.
 */
@SpringBootTest
@Transactional
class DeliverySchemaTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 25, 12, 0);

    @Autowired
    private DeliveryRepository deliveries;

    @Autowired
    private DeliveryLocationPointRepository points;

    private static Delivery storeLeg(String trackingNumber, long orderId, long buyer, long seller) {
        Delivery d = new Delivery();
        d.setTrackingNumber(trackingNumber);
        d.setDeliveryType(DeliveryType.STORE_TO_CUSTOMER);
        d.setOrderExternalId(orderId);
        d.setReferenceNumber("ORD-TEST-" + orderId);
        d.setCustomerAccountId(buyer);
        d.setPartnerAccountId(seller);
        d.setBookedByAccountId(seller);
        d.setOriginCode("SURAT_VARACHHA");
        d.setOriginLabel("Hirabaug Circle, Varachha Road");
        d.setOriginCity("Surat");
        d.setOriginLatitude(21.2040);
        d.setOriginLongitude(72.8590);
        d.setDestinationCode("AHD_MANINAGAR");
        d.setDestinationLabel("Jawahar Chowk, Maninagar");
        d.setDestinationCity("Ahmedabad");
        d.setDestinationLatitude(22.9962);
        d.setDestinationLongitude(72.6036);
        d.setRouteDistanceKm(250.0);
        d.setCurrentLatitude(21.2040);
        d.setCurrentLongitude(72.8590);
        d.setStatus(DeliveryStatus.BOOKED);
        d.setEstimatedArrivalAt(NOW.plusHours(7));
        d.setLastAdvancedAt(NOW);
        return d;
    }

    private static DeliveryLocationPoint point(Delivery d, int sequence) {
        return new DeliveryLocationPoint(d, sequence, DeliveryStatus.BOOKED, d.getOriginLatitude(),
                d.getOriginLongitude(), 0, d.getRouteDistanceKm(), "Simulated booking", d.getPartnerAccountId(), NOW);
    }

    @Test
    void roundTripWithHistoryAndPartyQuery() {
        Delivery d = deliveries.saveAndFlush(storeLeg("TRK-SIM-SCHEMA-001", 9101L, 71L, 72L));
        points.saveAndFlush(point(d, 0));

        assertThat(d.getId()).isNotNull();
        assertThat(d.getVersion()).isNotNull();
        assertThat(deliveries.findByTrackingNumber("TRK-SIM-SCHEMA-001")).isPresent();
        assertThat(deliveries.findForParty(71L)).extracting(Delivery::getTrackingNumber).contains("TRK-SIM-SCHEMA-001");
        assertThat(deliveries.findForParty(72L)).extracting(Delivery::getTrackingNumber).contains("TRK-SIM-SCHEMA-001");
        assertThat(deliveries.findForParty(73L)).isEmpty();
        assertThat(points.findByDelivery_IdOrderBySequenceNoAsc(d.getId())).hasSize(1);
        assertThat(d.isParty(71L)).isTrue();
        assertThat(d.isParty(73L)).isFalse();
        assertThat(d.isParty(null)).isFalse();
    }

    @Test
    void trackingNumbersAreUnique() {
        deliveries.saveAndFlush(storeLeg("TRK-SIM-SCHEMA-002", 9102L, 71L, 72L));

        assertThatThrownBy(() -> deliveries.saveAndFlush(storeLeg("TRK-SIM-SCHEMA-002", 9103L, 71L, 72L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void oneLegPerTypePerOrder() {
        deliveries.saveAndFlush(storeLeg("TRK-SIM-SCHEMA-003", 9104L, 71L, 72L));

        assertThatThrownBy(() -> deliveries.saveAndFlush(storeLeg("TRK-SIM-SCHEMA-004", 9104L, 71L, 72L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void historySequenceNumbersAreUniquePerDelivery() {
        Delivery d = deliveries.saveAndFlush(storeLeg("TRK-SIM-SCHEMA-005", 9105L, 71L, 72L));
        points.saveAndFlush(point(d, 0));

        assertThatThrownBy(() -> points.saveAndFlush(point(d, 0))).isInstanceOf(DataIntegrityViolationException.class);
    }
}
