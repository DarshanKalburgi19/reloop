package com.reloop.repair.repository;

import com.reloop.repair.entity.InventoryMovement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link InventoryMovement}.
 * Query methods are derived from their names - no SQL/JPQL needed.
 */
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    List<InventoryMovement> findBySparePart_IdOrderByCreatedAt(Long sparePartId);
    List<InventoryMovement> findByRepairRequest_Id(Long repairRequestId);

}
