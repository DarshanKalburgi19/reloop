package com.reloop.product.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.reloop.product.enums.AccountRole;
import com.reloop.product.enums.AccountStatus;
import java.time.LocalDateTime;

/**
 * Public view of an account. Built field-by-field from the entity (never serialises the entity),
 * so {@code password_hash} cannot leak. Exactly one of {@code customer} / {@code repairer} is set.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountResponse(
        Long id,
        AccountRole role,
        AccountStatus status,
        String name,
        String email,
        String phone,
        LocalDateTime createdAt,
        CustomerDetails customer,
        RepairerDetails repairer) {
}
