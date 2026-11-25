package com.reloop.repair.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Predefined Gujarat DEMO locations for simulated delivery tracking. Every delivery's origin and destination
 * is one of these points (public landmarks / market areas, approximate coordinates) — never a real customer
 * address and never a GPS reading. The first ten match the demo repairer workshops' neighbourhoods.
 */
public final class DemoLocations {

    /** A predefined demo point. {@code code} is what API clients send. */
    public record DemoLocation(String code, String label, String city, double latitude, double longitude) {
    }

    public static final List<DemoLocation> ALL = List.of(
            new DemoLocation("AHD_CG_ROAD", "CG Road, Navrangpura", "Ahmedabad", 23.0276, 72.5577),
            new DemoLocation("AHD_MANINAGAR", "Jawahar Chowk, Maninagar", "Ahmedabad", 22.9962, 72.6036),
            new DemoLocation("AHD_VASTRAPUR", "Vastrapur Lake, Vastrapur", "Ahmedabad", 23.0395, 72.5293),
            new DemoLocation("GNR_SECTOR_21", "Sector 21 Shopping Centre", "Gandhinagar", 23.2316, 72.6452),
            new DemoLocation("SURAT_ADAJAN", "Adajan Patiya", "Surat", 21.1940, 72.7932),
            new DemoLocation("SURAT_VARACHHA", "Hirabaug Circle, Varachha Road", "Surat", 21.2040, 72.8590),
            new DemoLocation("VADODARA_ALKAPURI", "R C Dutt Road, Alkapuri", "Vadodara", 22.3105, 73.1690),
            new DemoLocation("VADODARA_FATEHGUNJ", "Fatehgunj Circle", "Vadodara", 22.3235, 73.1846),
            new DemoLocation("RAJKOT_KALAWAD_ROAD", "KKV Chowk, Kalawad Road", "Rajkot", 22.2840, 70.7705),
            new DemoLocation("BHAVNAGAR_WAGHAWADI", "Waghawadi Road", "Bhavnagar", 21.7600, 72.1480),
            new DemoLocation("AHD_SATELLITE", "Shivranjani Cross Roads, Satellite", "Ahmedabad", 23.0258, 72.5260),
            new DemoLocation("ANAND_VIDYANAGAR", "Vallabh Vidyanagar", "Anand", 22.5530, 72.9230),
            new DemoLocation("MEHSANA_MODHERA_ROAD", "Modhera Road", "Mehsana", 23.5880, 72.3693),
            new DemoLocation("JAMNAGAR_BEDI_GATE", "Bedi Gate", "Jamnagar", 22.4707, 70.0577),
            new DemoLocation("JUNAGADH_KALWA_CHOWK", "Kalwa Chowk", "Junagadh", 21.5222, 70.4579),
            new DemoLocation("BHUJ_STATION_ROAD", "Station Road", "Bhuj", 23.2420, 69.6669));

    private static final Map<String, DemoLocation> BY_CODE = new LinkedHashMap<>();

    static {
        for (DemoLocation location : ALL) {
            BY_CODE.put(location.code(), location);
        }
    }

    private DemoLocations() {
    }

    /** Looks up a code case-insensitively ({@code "ahd_cg_road"} works). */
    public static Optional<DemoLocation> find(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_CODE.get(code.trim().toUpperCase(Locale.ROOT)));
    }

    /**
     * The demo point standing in for a workshop: the nearest one to its coordinates, else the first one in its
     * city, else empty.
     */
    public static Optional<DemoLocation> nearest(Double latitude, Double longitude, String city) {
        if (latitude != null && longitude != null) {
            return ALL.stream().min(Comparator.comparingDouble(
                    l -> GeoUtils.distanceKm(latitude, longitude, l.latitude(), l.longitude())));
        }
        if (city != null && !city.isBlank()) {
            String wanted = city.trim();
            return ALL.stream().filter(l -> l.city().equalsIgnoreCase(wanted)).findFirst();
        }
        return Optional.empty();
    }
}
