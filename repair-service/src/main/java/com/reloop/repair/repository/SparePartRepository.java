package com.reloop.repair.repository;

import com.reloop.repair.entity.SparePart;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link SparePart}.
 * Query methods are derived from their names - no SQL/JPQL needed.
 */
public interface SparePartRepository extends JpaRepository<SparePart, Long> {

    Optional<SparePart> findByRepairerProfile_IdAndSku(Long repairerProfileId, String sku);
    List<SparePart> findByRepairerProfile_Id(Long repairerProfileId);

}
