package com.reloop.product.security;

import com.reloop.product.enums.AccountRole;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The security principal placed in the SecurityContext after a valid JWT is presented.
 * It is built purely from verified token claims, so no database lookup is needed per request.
 *
 * @param accountId primary key of {@code accounts}
 * @param email     normalised (lower-case) e-mail
 * @param role      CUSTOMER or REPAIRER - mapped to Spring authority {@code ROLE_<role>}
 */
public record AuthenticatedAccount(Long accountId, String email, AccountRole role) {

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
