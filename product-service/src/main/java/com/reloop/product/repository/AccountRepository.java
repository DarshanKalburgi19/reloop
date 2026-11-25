package com.reloop.product.repository;

import com.reloop.product.entity.Account;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.reloop.product.enums.AccountRole;

/**
 * Spring Data JPA repository for {@link Account}.
 * Query methods are derived from their names - no SQL/JPQL needed.
 */
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByEmail(String email);
    boolean existsByEmail(String email);
    List<Account> findByRole(AccountRole role);

}
