package com.reloop.orderpayment.repository;

import com.reloop.orderpayment.entity.CommissionRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.reloop.orderpayment.enums.CommissionStatus;

/**
 * Spring Data JPA repository for {@link CommissionRecord}.
 * Query methods are derived from their names - no SQL/JPQL needed.
 */
public interface CommissionRecordRepository extends JpaRepository<CommissionRecord, Long> {

    Optional<CommissionRecord> findByTransactionId(String transactionId);
    Optional<CommissionRecord> findByPaymentTransactionId(Long paymentTransactionId);
    List<CommissionRecord> findByStatus(CommissionStatus status);

}
