package com.reloop.orderpayment.repository;

import com.reloop.orderpayment.entity.OrderItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link OrderItem}.
 * Query methods are derived from their names - no SQL/JPQL needed.
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrder_Id(Long orderId);

}
