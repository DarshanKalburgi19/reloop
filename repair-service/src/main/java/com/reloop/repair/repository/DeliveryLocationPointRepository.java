package com.reloop.repair.repository;

import com.reloop.repair.entity.DeliveryLocationPoint;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Append-only coordinate history of simulated deliveries. */
public interface DeliveryLocationPointRepository extends JpaRepository<DeliveryLocationPoint, Long> {

    List<DeliveryLocationPoint> findByDelivery_IdOrderBySequenceNoAsc(Long deliveryId);

    long countByDelivery_Id(Long deliveryId);
}
