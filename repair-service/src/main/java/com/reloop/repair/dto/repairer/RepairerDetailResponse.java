package com.reloop.repair.dto.repairer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.reloop.repair.enums.AvailabilityStatus;
import com.reloop.repair.enums.RepairMethod;
import com.reloop.repair.enums.RepairServiceCategory;
import com.reloop.repair.enums.RepairerProfileStatus;
import com.reloop.repair.enums.VerificationStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Public workshop profile ({@code GET /api/repairers/{id}}), also returned by create/update/me.
 *
 * <ul>
 *   <li>Location: {@code latitude}/{@code longitude} and the Leaflet literal {@code location}; {@code mapUrl}
 *       opens OpenStreetMap at the workshop.</li>
 *   <li>{@code listedInSearch} is false until the profile is complete; {@code missingForListing}
 *       says what is still needed (profiles created at registration start incomplete).</li>
 *   <li>{@code ownedByViewer} is true when the caller's token belongs to this workshop (show "Edit").</li>
 *   <li>The product-service account ID is not exposed.</li>
 * </ul>
 */
public record RepairerDetailResponse(
        Long id,
        String businessName,
        String description,
        List<RepairServiceCategory> categories,
        List<String> expertise,
        List<ServiceOfferingResponse> services,
        BigDecimal startingPrice,
        String startingPriceLabel,
        String phone,
        String addressLine,
        String city,
        String pincode,
        Double latitude,
        Double longitude,
        LatLng location,
        String mapUrl,
        List<WorkingHoursResponse> workingHours,
        String workingHoursSummary,
        List<RepairMethod> repairMethods,
        AvailabilityStatus availability,
        String availabilityNote,
        RatingSummary rating,
        List<ReviewResponse> recentReviews,
        VerificationStatus verificationStatus,
        RepairerProfileStatus status,
        boolean listedInSearch,
        List<String> missingForListing,
        boolean ownedByViewer,
        boolean demo,
        @JsonInclude(JsonInclude.Include.NON_NULL) String demoNotice,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
