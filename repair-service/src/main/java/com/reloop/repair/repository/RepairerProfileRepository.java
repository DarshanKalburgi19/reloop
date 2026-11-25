package com.reloop.repair.repository;

import com.reloop.repair.entity.RepairerProfile;
import com.reloop.repair.enums.RepairerProfileStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Spring Data JPA repository for {@link RepairerProfile}. Customer search and "nearby" use
 * {@link JpaSpecificationExecutor} with {@code RepairerSpecifications}.
 */
public interface RepairerProfileRepository extends JpaRepository<RepairerProfile, Long>,
        JpaSpecificationExecutor<RepairerProfile> {

    Optional<RepairerProfile> findByAccountExternalId(Long accountExternalId);
    List<RepairerProfile> findByStatus(RepairerProfileStatus status);
    List<RepairerProfile> findByBusinessNameContainingIgnoreCase(String businessName);

    /** Have the demo workshops (negative placeholder account IDs) been seeded already? */
    boolean existsByDemoTrueAndAccountExternalIdLessThan(Long accountExternalId);

    List<RepairerProfile> findByDemoTrue();
}
