package com.reloop.repair.repository;

import com.reloop.repair.entity.Delivery;
import com.reloop.repair.enums.DeliveryType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Simulated delivery legs. */
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    Optional<Delivery> findByTrackingNumber(String trackingNumber);

    boolean existsByTrackingNumber(String trackingNumber);

    List<Delivery> findByRepairRequest_IdOrderByIdAsc(Long repairRequestId);

    Optional<Delivery> findByRepairRequest_IdAndDeliveryType(Long repairRequestId, DeliveryType deliveryType);

    List<Delivery> findByOrderExternalIdOrderByIdAsc(Long orderExternalId);

    Optional<Delivery> findByOrderExternalIdAndDeliveryType(Long orderExternalId, DeliveryType deliveryType);

    /** Legs where the account is either party, newest first. */
    @Query("select d from Delivery d where d.customerAccountId = :account or d.partnerAccountId = :account order by d.id desc")
    List<Delivery> findForParty(@Param("account") Long accountId);
}
