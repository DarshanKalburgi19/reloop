package com.reloop.orderpayment.repository;

import com.reloop.orderpayment.entity.PaymentTransaction;
import com.reloop.orderpayment.enums.PaymentStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link PaymentTransaction}.
 */
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByTransactionId(String transactionId);

    /** Same row with a pessimistic write lock: serialises concurrent refunds of one payment. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentTransaction p where p.transactionId = :transactionId")
    Optional<PaymentTransaction> findByTransactionIdForUpdate(@Param("transactionId") String transactionId);

    boolean existsByTransactionId(String transactionId);

    List<PaymentTransaction> findByBuyerAccountId(Long buyerAccountId);

    List<PaymentTransaction> findByBuyerAccountIdOrderByIdDesc(Long buyerAccountId);

    List<PaymentTransaction> findByProviderAccountIdAndStatusInOrderByIdDesc(Long providerAccountId,
                                                                             Collection<PaymentStatus> statuses);

    List<PaymentTransaction> findByStatus(PaymentStatus status);

    Optional<PaymentTransaction> findByBuyerAccountIdAndIdempotencyKey(Long buyerAccountId, String idempotencyKey);

    /** The successful payment of a repair quote, if any (a quote is paid at most once). */
    Optional<PaymentTransaction> findFirstByRepairQuoteIdAndStatus(Long repairQuoteId, PaymentStatus status);

    /** The settled (successful, possibly later refunded) payment of a repair quote, if any. */
    Optional<PaymentTransaction> findFirstByRepairQuoteIdAndStatusIn(Long repairQuoteId, Collection<PaymentStatus> statuses);

    /** The settled payment of a marketplace order, if any. */
    Optional<PaymentTransaction> findFirstByPurchaseOrderIdAndStatusIn(Long purchaseOrderId, Collection<PaymentStatus> statuses);

    List<PaymentTransaction> findByRepairQuoteIdOrderByIdAsc(Long repairQuoteId);

    List<PaymentTransaction> findByPurchaseOrderIdOrderByIdAsc(Long purchaseOrderId);
}
