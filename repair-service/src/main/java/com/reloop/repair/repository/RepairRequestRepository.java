package com.reloop.repair.repository;

import com.reloop.repair.entity.RepairRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.reloop.repair.enums.RepairStatus;

/**
 * Spring Data JPA repository for {@link RepairRequest}.
 * Query methods are derived from their names - no SQL/JPQL needed.
 */
public interface RepairRequestRepository extends JpaRepository<RepairRequest, Long> {

    Optional<RepairRequest> findByRequestNumber(String requestNumber);
    List<RepairRequest> findByCustomerAccountId(Long customerAccountId);
    List<RepairRequest> findByRepairerProfile_Id(Long repairerProfileId);
    List<RepairRequest> findByStatus(RepairStatus status);

}
