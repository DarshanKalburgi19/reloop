package com.reloop.product.client;

/**
 * JSON sent to repair-service {@code POST /api/repairer-profiles}.
 * Field names match repair-service's {@code CreateRepairerProfileRequest}.
 * {@code demo} is true only for the seeded demo repairer, so repair-service labels its workshop as demo data.
 */
public record CreateRepairerProfileCommand(
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
        boolean demo) {
}
