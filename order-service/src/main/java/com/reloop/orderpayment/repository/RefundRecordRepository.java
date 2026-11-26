package com.reloop.orderpayment.repository;

import com.reloop.orderpayment.entity.RefundRecord;
import com.reloop.orderpayment.enums.RefundStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link RefundRecord}.
 */
public interface RefundRecordRepository extends JpaRepository<RefundRecord, Long> {

    List<RefundRecord> findByPaymentTransactionId(Long paymentTransactionId);

    List<RefundRecord> findByPaymentTransactionIdOrderByIdAsc(Long paymentTransactionId);

    Optional<RefundRecord> findByRefundReference(String refundReference);

    List<RefundRecord> findByStatus(RefundStatus status);

    Optional<RefundRecord> findByPaymentTransactionIdAndIdempotencyKey(Long paymentTransactionId, String idempotencyKey);
}
