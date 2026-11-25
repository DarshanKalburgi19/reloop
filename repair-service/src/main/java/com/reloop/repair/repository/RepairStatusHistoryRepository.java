package com.reloop.repair.repository;

import com.reloop.repair.entity.RepairStatusHistory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link RepairStatusHistory}.
 * Query methods are derived from their names - no SQL/JPQL needed.
 */
public interface RepairStatusHistoryRepository extends JpaRepository<RepairStatusHistory, Long> {

    List<RepairStatusHistory> findByRepairRequest_IdOrderByCreatedAtAsc(Long repairRequestId);

}
