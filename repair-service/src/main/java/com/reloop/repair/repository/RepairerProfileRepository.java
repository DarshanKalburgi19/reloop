package com.reloop.repair.repository;

import com.reloop.repair.entity.RepairerProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.reloop.repair.enums.RepairerProfileStatus;

/**
 * Spring Data JPA repository for {@link RepairerProfile}.
 * Query methods are derived from their names - no SQL/JPQL needed.
 */
public interface RepairerProfileRepository extends JpaRepository<RepairerProfile, Long> {

    Optional<RepairerProfile> findByAccountExternalId(Long accountExternalId);
    List<RepairerProfile> findByStatus(RepairerProfileStatus status);
    List<RepairerProfile> findByBusinessNameContainingIgnoreCase(String businessName);

}
