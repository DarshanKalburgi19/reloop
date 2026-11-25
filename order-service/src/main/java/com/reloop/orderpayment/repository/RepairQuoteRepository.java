package com.reloop.orderpayment.repository;

import com.reloop.orderpayment.entity.RepairQuote;
import com.reloop.orderpayment.enums.QuoteStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link RepairQuote}.
 */
public interface RepairQuoteRepository extends JpaRepository<RepairQuote, Long> {

    Optional<RepairQuote> findFirstByRepairRequestExternalIdOrderByQuoteRevisionDesc(Long repairRequestId);

    /** Pessimistic write lock: two simultaneous payments of one quote are processed one after the other. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from RepairQuote q where q.id = :id")
    Optional<RepairQuote> findByIdForUpdate(@Param("id") Long id);

    List<RepairQuote> findByRepairRequestExternalId(Long repairRequestId);

    List<RepairQuote> findByStatus(QuoteStatus status);
}
