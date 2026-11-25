package com.reloop.product.repository;

import com.reloop.product.entity.RepairerAccountIdentity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link RepairerAccountIdentity}.
 * Query methods are derived from their names - no SQL/JPQL needed.
 */
public interface RepairerAccountIdentityRepository extends JpaRepository<RepairerAccountIdentity, Long> {

    Optional<RepairerAccountIdentity> findByAccount_Id(Long accountId);

}
