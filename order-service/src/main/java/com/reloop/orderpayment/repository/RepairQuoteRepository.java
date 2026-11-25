package com.reloop.orderpayment.repository;

import com.reloop.orderpayment.entity.RepairQuote;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.reloop.orderpayment.enums.QuoteStatus;

/**
 * Spring Data JPA repository for {@link RepairQuote}.
 * Query methods are derived from their names - no SQL/JPQL needed.
 */
public interface RepairQuoteRepository extends JpaRepository<RepairQuote, Long> {

    Optional<RepairQuote> findFirstByRepairRequestExternalIdOrderByQuoteRevisionDesc(Long repairRequestId);
    List<RepairQuote> findByRepairRequestExternalId(Long repairRequestId);
    List<RepairQuote> findByStatus(QuoteStatus status);

}
