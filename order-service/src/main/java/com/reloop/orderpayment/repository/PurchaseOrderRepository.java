package com.reloop.orderpayment.repository;

import com.reloop.orderpayment.entity.PurchaseOrder;
import com.reloop.orderpayment.enums.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link PurchaseOrder}.
 */
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByOrderNumber(String orderNumber);

    /** Pessimistic write lock: one payment / cancellation / expiry at a time per order. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("id") Long id);

    List<PurchaseOrder> findByBuyerAccountId(Long buyerAccountId);

    List<PurchaseOrder> findByBuyerAccountIdOrderByIdDesc(Long buyerAccountId);

    List<PurchaseOrder> findBySellerAccountId(Long sellerAccountId);

    List<PurchaseOrder> findBySellerAccountIdOrderByIdDesc(Long sellerAccountId);

    List<PurchaseOrder> findByStatus(OrderStatus status);

    Optional<PurchaseOrder> findFirstByBuyerAccountIdAndProductExternalIdAndStatus(Long buyerAccountId, Long productId,
                                                                                    OrderStatus status);

    /** Unpaid orders past their payment window. */
    List<PurchaseOrder> findTop50ByStatusAndExpiresAtBeforeOrderByIdAsc(OrderStatus status, LocalDateTime now);

    /** Cancelled orders whose product reservation still has to be released in product-service. */
    List<PurchaseOrder> findTop50ByStatusAndProductReleasedFalseOrderByIdAsc(OrderStatus status);
}
