package com.reloop.product.repository;

import com.reloop.product.entity.CustomerAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link CustomerAccount}.
 * Query methods are derived from their names - no SQL/JPQL needed.
 */
public interface CustomerAccountRepository extends JpaRepository<CustomerAccount, Long> {

    Optional<CustomerAccount> findByAccount_Id(Long accountId);

}
