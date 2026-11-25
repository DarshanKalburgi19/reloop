package com.reloop.orderpayment.repository;

import com.reloop.orderpayment.entity.PurchaseOrder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.reloop.orderpayment.enums.OrderStatus;

/**
 * Spring Data JPA repository for {@link PurchaseOrder}.
 * Query methods are derived from their names - no SQL/JPQL needed.
 */
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByOrderNumber(String orderNumber);
    List<PurchaseOrder> findByBuyerAccountId(Long buyerAccountId);
    List<PurchaseOrder> findBySellerAccountId(Long sellerAccountId);
    List<PurchaseOrder> findByStatus(OrderStatus status);

}
