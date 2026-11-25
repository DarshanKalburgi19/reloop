package com.reloop.repair.repository;

import com.reloop.repair.entity.InventoryMovement;
import com.reloop.repair.enums.InventoryMovementType;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for the append-only {@link InventoryMovement} ledger. */
public interface InventoryMovementRepository
        extends JpaRepository<InventoryMovement, Long>, JpaSpecificationExecutor<InventoryMovement> {

    List<InventoryMovement> findBySparePart_IdOrderByCreatedAt(Long sparePartId);

    List<InventoryMovement> findByRepairRequest_Id(Long repairRequestId);

    Page<InventoryMovement> findBySparePart_Id(Long sparePartId, Pageable pageable);

    List<InventoryMovement> findByRepairRequest_IdAndRepairerProfile_IdOrderByMovementDateAscIdAsc(
            Long repairRequestId, Long repairerProfileId);

    long countBySparePart_Id(Long sparePartId);

    /** Σ |quantity| × unit cost (paise) of one movement type for a repairer; null when there are none. */
    @Query("select sum(abs(m.quantityChange) * m.unitCostPaise) from InventoryMovement m"
            + " where m.repairerProfile.id = :profileId and m.movementType = :type")
    Long sumCostPaise(@Param("profileId") Long profileId, @Param("type") InventoryMovementType type);
}
