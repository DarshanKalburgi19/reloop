package com.reloop.repair.dto.repairrequest;

import jakarta.validation.constraints.Size;

/**
 * Optional free-text note for an action (accept, approve, progress...). Some actions require it:
 * reject, cancel and progress updates (the service reports {@code fieldErrors.note}).
 */
public record StatusNoteRequest(@Size(max = RepairRequestValidation.MAX_NOTE) String note) {

    public static final StatusNoteRequest EMPTY = new StatusNoteRequest(null);
}
