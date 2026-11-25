package com.reloop.repair.dto.repairer;

import com.reloop.repair.enums.AvailabilityStatus;
import com.reloop.repair.enums.RepairMethod;
import com.reloop.repair.enums.RepairServiceCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;

/**
 * Body of {@code POST /api/repairers} (create) and {@code PUT /api/repairers/{id}} (full replacement).
 *
 * <p>Coordinates are WGS84 decimal degrees, exactly what Leaflet uses ({@code L.marker([latitude, longitude])}).
 * They must be inside India; swapped latitude/longitude is detected and reported.</p>
 *
 * <p>Cross-field rules (checked in the service): every service's category must be one of
 * {@code categories}; service names and working-hour days must be unique; {@code opens < closes}.</p>
 */
public record RepairerProfileRequest(
        @NotBlank @Size(min = 3, max = 150) String businessName,
        @NotBlank @Size(min = 20, max = 2000) String description,
        @NotEmpty @Size(max = 8) Set<@NotNull RepairServiceCategory> categories,
        @Size(max = RepairerValidation.MAX_EXPERTISE) List<@NotBlank @Size(min = 2, max = 40)
                @Pattern(regexp = RepairerValidation.EXPERTISE, message = "must not contain commas or semicolons") String> expertise,
        @NotEmpty @Size(max = RepairerValidation.MAX_SERVICES) List<@NotNull @Valid ServiceOfferingRequest> services,
        @Pattern(regexp = RepairerValidation.PHONE, message = "must be 10-15 digits, optionally starting with +") String phone,
        @NotBlank @Size(max = 255) String addressLine,
        @NotBlank @Size(max = 120) @Pattern(regexp = RepairerValidation.CITY, message = "must be a city name") String city,
        @NotBlank @Pattern(regexp = RepairerValidation.PINCODE, message = "must be a 6-digit Indian PIN code") String pincode,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotEmpty @Size(max = 7) List<@NotNull @Valid WorkingHoursRequest> workingHours,
        @NotEmpty Set<@NotNull RepairMethod> repairMethods,
        @NotNull AvailabilityStatus availability,
        @Size(max = 200) String availabilityNote) {
}
