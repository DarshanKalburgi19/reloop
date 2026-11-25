package com.reloop.repair.service;

/**
 * Geographic helpers for workshop locations (WGS84 decimal degrees, as used by Leaflet/OpenStreetMap).
 */
public final class GeoUtils {

    /** Mean Earth radius (IUGG). */
    public static final double EARTH_RADIUS_KM = 6371.0088;

    /** Rough bounding box of India; workshop coordinates must be inside it. */
    public static final double INDIA_MIN_LAT = 6.5;
    public static final double INDIA_MAX_LAT = 37.5;
    public static final double INDIA_MIN_LNG = 68.0;
    public static final double INDIA_MAX_LNG = 97.5;

    private GeoUtils() {
    }

    /** Great-circle (haversine) distance in km. */
    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /**
     * A box that fully contains the circle of {@code radiusKm} around the point (with a 1% margin).
     * Used as a cheap indexed SQL pre-filter before the exact distance check.
     */
    public static BoundingBox boundingBox(double lat, double lng, double radiusKm) {
        double dLat = Math.toDegrees(radiusKm / EARTH_RADIUS_KM) * 1.01;
        double cosLat = Math.max(0.01, Math.cos(Math.toRadians(lat)));
        double dLng = Math.min(180.0, Math.toDegrees(radiusKm / (EARTH_RADIUS_KM * cosLat)) * 1.01);
        return new BoundingBox(Math.max(-90.0, lat - dLat), Math.max(-180.0, lng - dLng),
                Math.min(90.0, lat + dLat), Math.min(180.0, lng + dLng));
    }

    public static boolean inIndia(double lat, double lng) {
        return lat >= INDIA_MIN_LAT && lat <= INDIA_MAX_LAT && lng >= INDIA_MIN_LNG && lng <= INDIA_MAX_LNG;
    }

    /** 6 decimals = about 0.1 m; more precision is noise. */
    public static double round6(double degrees) {
        return Math.round(degrees * 1_000_000d) / 1_000_000d;
    }

    public static double round1(double value) {
        return Math.round(value * 10d) / 10d;
    }

    /** south/west/north/east in decimal degrees. */
    public record BoundingBox(double south, double west, double north, double east) {
    }
}
