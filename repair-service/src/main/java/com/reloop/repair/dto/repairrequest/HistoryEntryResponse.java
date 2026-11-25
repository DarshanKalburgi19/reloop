package com.reloop.repair.dto.repairrequest;

import com.reloop.repair.enums.RepairStatus;
import com.reloop.repair.enums.StatusChangeActor;
import java.time.LocalDateTime;

/** One timestamped timeline entry. Progress notes have {@code fromStatus == toStatus == IN_PROGRESS}. */
public record HistoryEntryResponse(
        RepairStatus fromStatus,
        RepairStatus toStatus,
        StatusChangeActor actor,
        String note,
        LocalDateTime at) {
}
