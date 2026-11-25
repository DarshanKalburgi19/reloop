package com.reloop.orderpayment.repository;

import com.reloop.orderpayment.entity.PaymentTransaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.reloop.orderpayment.enums.PaymentStatus;

/**
 * Spring Data JPA repository for {@link PaymentTransaction}.
 * Query methods are derived from their names - no SQL/JPQL needed.
 */
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByTransactionId(String transactionId);
    boolean existsByTransactionId(String transactionId);
    List<PaymentTransaction> findByBuyerAccountId(Long buyerAccountId);
    List<PaymentTransaction> findByStatus(PaymentStatus status);

    /** The successful payment of a repair quote, if any (a quote is paid at most once). */
    Optional<PaymentTransaction> findFirstByRepairQuoteIdAndStatus(Long repairQuoteId, PaymentStatus status);

    List<PaymentTransaction> findByRepairQuoteIdOrderByIdAsc(Long repairQuoteId);

}
