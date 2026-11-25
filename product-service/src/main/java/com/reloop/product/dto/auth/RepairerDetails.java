package com.reloop.product.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.reloop.product.enums.VerificationStatus;

/**
 * Repairer-specific part of {@link AccountResponse}.
 *
 * <p>{@code verificationStatus} is stored locally (repairer_account_identities) so it is always
 * present. {@code workshop} is owned by repair-service; it is omitted from {@code /me} when
 * repair-service is unreachable.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RepairerDetails(
        Long repairerProfileId,
        VerificationStatus verificationStatus,
        WorkshopDetails workshop) {
}
