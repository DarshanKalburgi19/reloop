package com.reloop.repair.repository;

import com.reloop.repair.entity.SparePart;
import com.reloop.repair.enums.SparePartStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@link SparePart}. Every finder is scoped to the owning repairer. */
public interface SparePartRepository extends JpaRepository<SparePart, Long>, JpaSpecificationExecutor<SparePart> {

    Optional<SparePart> findByRepairerProfile_IdAndSku(Long repairerProfileId, String sku);

    List<SparePart> findByRepairerProfile_Id(Long repairerProfileId);

    List<SparePart> findByRepairerProfile_IdAndStatus(Long repairerProfileId, SparePartStatus status);

    Optional<SparePart> findByIdAndRepairerProfile_Id(Long id, Long repairerProfileId);

    boolean existsByRepairerProfile_IdAndSku(Long repairerProfileId, String sku);

    boolean existsByRepairerProfile_IdAndSkuAndIdNot(Long repairerProfileId, String sku, Long id);

    /**
     * The owner's part, row-locked until the transaction ends ({@code SELECT … FOR UPDATE}). All stock changes
     * go through this, so two concurrent "use 1 unit" calls can't both see the last unit.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SparePart p where p.id = :id and p.repairerProfile.id = :profileId")
    Optional<SparePart> findOwnedForUpdate(@Param("id") Long id, @Param("profileId") Long profileId);

    /** Active parts at or below their threshold, most urgent first. */
    @Query("select p from SparePart p where p.repairerProfile.id = :profileId and p.status = :status"
            + " and p.quantity <= p.lowStockThreshold order by p.quantity asc, p.partName asc")
    List<SparePart> findLowStock(@Param("profileId") Long profileId, @Param("status") SparePartStatus status);
}
