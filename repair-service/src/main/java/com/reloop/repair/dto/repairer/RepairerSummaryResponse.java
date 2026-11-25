package com.reloop.repair.dto.repairer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.reloop.repair.enums.AvailabilityStatus;
import com.reloop.repair.enums.RepairMethod;
import com.reloop.repair.enums.RepairServiceCategory;
import com.reloop.repair.enums.VerificationStatus;
import java.math.BigDecimal;
import java.util.List;

/**
 * Search result card (and map marker). {@code startingPrice} is the lowest price among the
 * services matching the search's category/price filters (all active services when unfiltered).
 * {@code distanceKm} is only present in {@code /nearby} results.
 */
public record RepairerSummaryResponse(
        Long id,
        String businessName,
        String city,
        String addressLine,
        Double latitude,
        Double longitude,
        LatLng location,
        List<RepairServiceCategory> categories,
        List<RepairMethod> repairMethods,
        BigDecimal startingPrice,
        String startingPriceLabel,
        RatingSummary rating,
        AvailabilityStatus availability,
        VerificationStatus verificationStatus,
        boolean demo,
        @JsonInclude(JsonInclude.Include.NON_NULL) String demoNotice,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double distanceKm) {

    public RepairerSummaryResponse withDistance(double km) {
        return new RepairerSummaryResponse(id, businessName, city, addressLine, latitude, longitude, location,
                categories, repairMethods, startingPrice, startingPriceLabel, rating, availability,
                verificationStatus, demo, demoNotice, km);
    }
}
