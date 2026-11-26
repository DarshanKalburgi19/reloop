package com.reloop.repair.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.reloop.repair.service.DemoLocations.DemoLocation;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The predefined Gujarat demo locations behind simulated delivery tracking. */
class DemoLocationsTest {

    @Test
    void everyLocationIsInsideGujaratWithAUniqueCode() {
        assertThat(DemoLocations.ALL).hasSizeGreaterThanOrEqualTo(10);
        Set<String> codes = DemoLocations.ALL.stream().map(DemoLocation::code).collect(Collectors.toSet());
        assertThat(codes).hasSize(DemoLocations.ALL.size());
        assertThat(DemoLocations.ALL).allSatisfy(l -> {
            // Gujarat's bounding box (approx.)
            assertThat(l.latitude()).isBetween(20.0, 24.8);
            assertThat(l.longitude()).isBetween(68.0, 74.6);
            assertThat(l.code()).matches("[A-Z][A-Z0-9_]+");
            assertThat(l.label()).isNotBlank();
            assertThat(l.city()).isNotBlank();
        });
        assertThat(DemoLocations.ALL).extracting(DemoLocation::city)
                .contains("Ahmedabad", "Surat", "Vadodara", "Rajkot", "Gandhinagar", "Bhavnagar");
    }

    @Test
    void findIsCaseInsensitiveAndRejectsUnknownCodes() {
        assertThat(DemoLocations.find("ahd_cg_road")).map(DemoLocation::city).contains("Ahmedabad");
        assertThat(DemoLocations.find(" SURAT_ADAJAN ")).isPresent();
        assertThat(DemoLocations.find("MUMBAI_BANDRA")).isEmpty();
        assertThat(DemoLocations.find(null)).isEmpty();
        assertThat(DemoLocations.find(" ")).isEmpty();
    }

    @Test
    void nearestPicksTheClosestPointThenFallsBackToTheCity() {
        // the demo workshop in Varachha, Surat
        assertThat(DemoLocations.nearest(21.2045, 72.8585, null)).map(DemoLocation::code).contains("SURAT_VARACHHA");
        // Ravi Mehta's demo workshop near CG Road, Ahmedabad
        assertThat(DemoLocations.nearest(23.0225, 72.5714, "Ahmedabad")).map(DemoLocation::city).contains("Ahmedabad");
        assertThat(DemoLocations.nearest(null, null, "rajkot")).map(DemoLocation::code).contains("RAJKOT_KALAWAD_ROAD");
        assertThat(DemoLocations.nearest(null, null, "Pune")).isEmpty();
        assertThat(DemoLocations.nearest(null, null, null)).isEmpty();
    }
}
