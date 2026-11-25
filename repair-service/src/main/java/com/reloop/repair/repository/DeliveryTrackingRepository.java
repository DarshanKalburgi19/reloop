package com.reloop.repair.repository;

import com.reloop.repair.entity.DeliveryTracking;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link DeliveryTracking}.
 * Query methods are derived from their names - no SQL/JPQL needed.
 */
public interface DeliveryTrackingRepository extends JpaRepository<DeliveryTracking, Long> {

    List<DeliveryTracking> findByRepairRequest_Id(Long repairRequestId);

}
