package com.reloop.product.dto.auth;

import com.reloop.product.enums.RepairMethod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;

/**
 * Body of {@code POST /api/auth/register/repairer}.
 *
 * <p>There is intentionally <b>no verificationStatus field</b>: a repairer can never verify
 * themselves. Every new repairer starts as {@code UNVERIFIED}; any value sent by the client is
 * ignored. The status is returned in responses.</p>
 */
public record RepairerRegistrationRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = ValidationPatterns.PHONE, message = "must be 10-15 digits, optionally starting with +") String phone,
        @NotBlank @Size(min = 8, max = 72) @Pattern(regexp = ValidationPatterns.PASSWORD, message = ValidationPatterns.PASSWORD_MESSAGE) String password,
        @NotBlank @Size(max = 150) String workshopName,
        @NotEmpty @Size(max = 10) List<@NotBlank @Size(max = 40) String> repairExpertise,
        @NotBlank @Size(max = 255) String workshopAddress,
        @NotBlank @Size(max = 120) String city,
        @Pattern(regexp = ValidationPatterns.PINCODE, message = "must be a 6-digit Indian PIN code") String pincode,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotBlank @Size(max = 300) String serviceHours,
        @NotEmpty Set<@NotNull RepairMethod> repairMethods) {

    @Override
    public String toString() {
        return "RepairerRegistrationRequest[email=" + email + ", workshopName=" + workshopName + ", password=****]";
    }
}
