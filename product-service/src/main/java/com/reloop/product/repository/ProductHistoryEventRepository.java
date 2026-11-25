package com.reloop.product.repository;

import com.reloop.product.entity.ProductHistoryEvent;
import com.reloop.product.enums.ProductHistoryEventType;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link ProductHistoryEvent} (append-only). */
public interface ProductHistoryEventRepository extends JpaRepository<ProductHistoryEvent, Long> {

    /** Full passport timeline, oldest first; actor fetched eagerly for the response. */
    @EntityGraph(attributePaths = {"actor", "product"})
    List<ProductHistoryEvent> findByPassport_IdOrderByCreatedAtAscIdAsc(Long passportId);

    List<ProductHistoryEvent> findByProduct_IdAndEventType(Long productId, ProductHistoryEventType eventType);
}
