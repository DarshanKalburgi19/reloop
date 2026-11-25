package com.reloop.product.dto.auth;

/** Customer-specific part of {@link AccountResponse}. */
public record CustomerDetails(
        String firstName,
        String lastName,
        String address,
        String city,
        String state,
        String pincode) {
}
