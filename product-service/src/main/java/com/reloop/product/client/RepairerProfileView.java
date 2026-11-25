package com.reloop.product.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** JSON returned by repair-service for a repairer profile (unknown fields are ignored). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RepairerProfileView(
        Long id,
        Long accountExternalId,
        String businessName,
        String specialties,
        String phone,
        String addressLine,
        String city,
        String pincode,
        Double latitude,
        Double longitude,
        String serviceHours,
        boolean acceptsInPerson,
        boolean acceptsPickupDrop,
        boolean acceptsShipping,
        String verificationStatus,
        String status) {
}
