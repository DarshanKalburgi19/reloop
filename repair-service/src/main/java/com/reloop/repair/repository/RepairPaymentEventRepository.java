package com.reloop.repair.repository;

import com.reloop.repair.entity.RepairPaymentEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepairPaymentEventRepository extends JpaRepository<RepairPaymentEvent, Long> {

    boolean existsByEventId(String eventId);

    List<RepairPaymentEvent> findByRepairRequestIdOrderByIdAsc(Long repairRequestId);
}
