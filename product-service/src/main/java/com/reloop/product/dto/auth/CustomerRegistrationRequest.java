package com.reloop.product.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/auth/register/customer}.
 *
 * <p>{@code pincode} is required because every stored address needs one (addresses.pincode is
 * NOT NULL - it is used for delivery and pickup). {@code state} is optional and only used when
 * the city is not yet in the {@code cities} lookup table.</p>
 */
public record CustomerRegistrationRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = ValidationPatterns.PHONE, message = "must be 10-15 digits, optionally starting with +") String phone,
        @NotBlank @Size(min = 8, max = 72) @Pattern(regexp = ValidationPatterns.PASSWORD, message = ValidationPatterns.PASSWORD_MESSAGE) String password,
        @NotBlank @Size(max = 255) String address,
        @NotBlank @Size(max = 120) String city,
        @NotBlank @Pattern(regexp = ValidationPatterns.PINCODE, message = "must be a 6-digit Indian PIN code") String pincode,
        @Size(max = 120) String state) {

    /** Never print the password, even in logs or exception messages. */
    @Override
    public String toString() {
        return "CustomerRegistrationRequest[email=" + email + ", city=" + city + ", password=****]";
    }
}
