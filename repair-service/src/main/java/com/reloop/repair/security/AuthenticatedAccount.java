package com.reloop.repair.security;

import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The caller, as stated by a verified product-service access token.
 * {@code accountId} is the product-service Account ID ({@code sub} claim);
 * {@code role} is {@code CUSTOMER} or {@code REPAIRER}.
 */
public record AuthenticatedAccount(Long accountId, String email, String role) {

    public static final String CUSTOMER = "CUSTOMER";
    public static final String REPAIRER = "REPAIRER";

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    public boolean isRepairer() {
        return REPAIRER.equals(role);
    }
}
