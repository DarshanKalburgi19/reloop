package com.reloop.repair.dto.tracking;

import com.reloop.repair.service.DemoLocations.DemoLocation;

/** A predefined Gujarat demo location (not a real address, not a GPS fix). */
public record DemoLocationResponse(String code, String label, String city, double latitude, double longitude) {

    public static DemoLocationResponse of(DemoLocation l) {
        return new DemoLocationResponse(l.code(), l.label(), l.city(), l.latitude(), l.longitude());
    }
}
