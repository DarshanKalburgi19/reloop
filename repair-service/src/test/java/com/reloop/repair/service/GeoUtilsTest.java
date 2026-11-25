package com.reloop.repair.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GeoUtilsTest {

    @Test
    void haversineDistanceBetweenGujaratCities() {
        // Ahmedabad (CG Road) -> Gandhinagar (Sector 21): ~24.4 km
        assertThat(GeoUtils.distanceKm(23.0225, 72.5714, 23.2316, 72.6452)).isCloseTo(24.4, within(0.2));
        // Ahmedabad -> Surat: ~205 km as the crow flies
        assertThat(GeoUtils.distanceKm(23.0225, 72.5714, 21.1940, 72.7932)).isCloseTo(204.6, within(1.0));
        assertThat(GeoUtils.distanceKm(23.0, 72.5, 23.0, 72.5)).isZero();
    }

    @Test
    void boundingBoxContainsTheWholeCircle() {
        GeoUtils.BoundingBox box = GeoUtils.boundingBox(23.0225, 72.5714, 10);
        assertThat(box.south()).isLessThan(23.0225);
        assertThat(box.north()).isGreaterThan(23.0225);
        // points exactly 10 km north/east are inside the box
        assertThat(23.0225 + Math.toDegrees(10 / GeoUtils.EARTH_RADIUS_KM)).isLessThan(box.north());
        double tenKmEast = 72.5714 + Math.toDegrees(10 / (GeoUtils.EARTH_RADIUS_KM * Math.cos(Math.toRadians(23.0225))));
        assertThat(tenKmEast).isLessThan(box.east());
        assertThat(GeoUtils.distanceKm(23.0225, 72.5714, 23.0225, box.east())).isGreaterThan(10);
    }

    @Test
    void indiaCheckAndRounding() {
        assertThat(GeoUtils.inIndia(23.0225, 72.5714)).isTrue();
        assertThat(GeoUtils.inIndia(72.5714, 23.0225)).isFalse();
        assertThat(GeoUtils.inIndia(51.5072, -0.1276)).isFalse();
        assertThat(GeoUtils.round6(23.02251234)).isEqualTo(23.022512);
        assertThat(GeoUtils.round1(4.75)).isEqualTo(4.8);
    }
}
