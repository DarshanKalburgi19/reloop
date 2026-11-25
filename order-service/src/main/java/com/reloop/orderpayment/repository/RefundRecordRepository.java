package com.reloop.orderpayment.repository;

import com.reloop.orderpayment.entity.RefundRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.reloop.orderpayment.enums.RefundStatus;

/**
 * Spring Data JPA repository for {@link RefundRecord}.
 * Query methods are derived from their names - no SQL/JPQL needed.
 */
public interface RefundRecordRepository extends JpaRepository<RefundRecord, Long> {

    List<RefundRecord> findByPaymentTransactionId(Long paymentTransactionId);
    List<RefundRecord> findByStatus(RefundStatus status);

}
