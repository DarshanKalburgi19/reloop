package com.reloop.repair.dto.repairrequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/repair-requests/{id}/inspect} (assigned REPAIRER). */
public record InspectionRequest(@NotBlank @Size(min = 10, max = 2000) String findings) {
}
