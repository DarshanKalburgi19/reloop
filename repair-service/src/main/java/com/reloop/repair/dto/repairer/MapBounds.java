package com.reloop.repair.dto.repairer;

/** Same shape as Leaflet's {@code LatLngBounds}: {@code map.fitBounds([[sw.lat, sw.lng], [ne.lat, ne.lng]])}. */
public record MapBounds(LatLng southWest, LatLng northEast) {
}
