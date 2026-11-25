package com.reloop.repair.dto.tracking;

/** An end of a delivery leg: a snapshot of the demo location chosen at booking. */
public record DeliveryLocationResponse(String code, String label, String city, double latitude, double longitude) {
}
