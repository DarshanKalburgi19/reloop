package com.reloop.repair.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body of the internal {@code POST /api/repairer-profiles}, called by product-service during
 * repairer registration. No verificationStatus field: new profiles are always UNVERIFIED.
 */
public record CreateRepairerProfileRequest(
        @NotNull @Positive Long accountExternalId,
        @NotBlank @Size(max = 150) String businessName,
        @Size(max = 500) String specialties,
        @Size(max = 30) String phone,
        @NotBlank @Size(max = 255) String addressLine,
        @NotBlank @Size(max = 120) String city,
        @Pattern(regexp = "^[1-9][0-9]{5}$", message = "must be a 6-digit Indian PIN code") String pincode,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotBlank @Size(max = 300) String serviceHours,
        boolean acceptsInPerson,
        boolean acceptsPickupDrop,
        boolean acceptsShipping) {
}
