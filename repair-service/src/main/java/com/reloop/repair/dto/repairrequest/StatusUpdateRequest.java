package com.reloop.repair.dto.repairrequest;

import com.reloop.repair.enums.RepairStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PATCH /api/repair-requests/{id}/status} (assigned REPAIRER): a generic form of the
 * repairer actions. Allowed targets: ACCEPTED, REJECTED, INSPECTED (note = findings), IN_PROGRESS, COMPLETED.
 * Quotes need an amount, so they use {@code POST /quote}.
 */
public record StatusUpdateRequest(@NotNull RepairStatus status,
                                  @Size(max = 2000) String note) {
}
