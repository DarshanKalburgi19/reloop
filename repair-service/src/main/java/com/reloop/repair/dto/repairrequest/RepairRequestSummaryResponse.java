package com.reloop.repair.dto.repairrequest;

import com.reloop.repair.enums.RepairMethod;
import com.reloop.repair.enums.RepairServiceCategory;
import com.reloop.repair.enums.RepairPaymentStatus;
import com.reloop.repair.enums.RepairStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One row of the customer's history or the repairer's inbox. */
public record RepairRequestSummaryResponse(
        Long id,
        String requestNumber,
        RepairStatus status,
        Long customerAccountId,
        Long repairerProfileId,
        String repairerBusinessName,
        RepairServiceCategory category,
        String itemDescription,
        RepairMethod deliveryMethod,
        BigDecimal quoteTotalPayable,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        RepairPaymentStatus paymentStatus,
        String transactionId) {
}
