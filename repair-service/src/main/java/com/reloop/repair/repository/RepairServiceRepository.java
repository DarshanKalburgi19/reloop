package com.reloop.repair.repository;

import com.reloop.repair.entity.RepairService;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link RepairService}.
 * Query methods are derived from their names - no SQL/JPQL needed.
 */
public interface RepairServiceRepository extends JpaRepository<RepairService, Long> {

    List<RepairService> findByRepairerProfile_Id(Long repairerProfileId);

}
