package com.reloop.repair.repository;

import com.reloop.repair.entity.RepairRequest;
import com.reloop.repair.enums.RepairStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link RepairRequest}.
 * Query methods are derived from their names - no SQL/JPQL needed.
 */
public interface RepairRequestRepository extends JpaRepository<RepairRequest, Long> {

    Optional<RepairRequest> findByRequestNumber(String requestNumber);
    boolean existsByRequestNumber(String requestNumber);
    List<RepairRequest> findByCustomerAccountId(Long customerAccountId);
    List<RepairRequest> findByRepairerProfile_Id(Long repairerProfileId);
    List<RepairRequest> findByStatus(RepairStatus status);

    /* customer history / repairer inbox (newest first via the Pageable sort) */
    Page<RepairRequest> findByCustomerAccountId(Long customerAccountId, Pageable pageable);
    Page<RepairRequest> findByCustomerAccountIdAndStatusIn(Long customerAccountId, Collection<RepairStatus> statuses,
                                                           Pageable pageable);
    Page<RepairRequest> findByRepairerProfile_Id(Long repairerProfileId, Pageable pageable);
    Page<RepairRequest> findByRepairerProfile_IdAndStatusIn(Long repairerProfileId, Collection<RepairStatus> statuses,
                                                            Pageable pageable);
}
