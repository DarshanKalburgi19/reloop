package com.reloop.repair.dto.repairrequest;

import com.reloop.repair.enums.RepairMethod;
import com.reloop.repair.enums.RepairServiceCategory;
import com.reloop.repair.enums.RepairPaymentStatus;
import com.reloop.repair.enums.RepairStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Full repair request as seen by its customer or its assigned repairer.
 * {@code allowedActions} lists what THIS viewer can do next (e.g. ["approve-quote", "reject-quote", "cancel"]),
 * so a UI can show the right buttons. {@code history} is the timestamped status timeline, oldest first.
 */
public record RepairRequestResponse(
        Long id,
        String requestNumber,
        RepairStatus status,
        Long customerAccountId,
        RepairerCard repairer,
        Long repairServiceId,
        String repairServiceName,
        RepairServiceCategory category,
        String itemDescription,
        String problemDescription,
        List<String> imageUrls,
        RepairMethod deliveryMethod,
        String deliveryInstructions,
        String customerNotes,
        String inspectionNotes,
        QuoteResponse quote,
        Long paymentTransactionId,
        String transactionId,
        RepairPaymentStatus paymentStatus,
        long refundedAmountPaise,
        String lastPaymentFailure,
        String closeReason,
        List<String> allowedActions,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime acceptedAt,
        LocalDateTime inspectedAt,
        LocalDateTime quotedAt,
        LocalDateTime approvedAt,
        LocalDateTime paidAt,
        LocalDateTime inProgressAt,
        LocalDateTime completedAt,
        LocalDateTime closedAt,
        List<HistoryEntryResponse> history) {
}
