package reloop.repair.enums;

/**
 * RepairStatus - fixed status/choice enum (stored as string, never ordinal).
 *
 * <p>Repair request lifecycle: REQUESTED -> ACCEPTED -> INSPECTED -> QUOTED ->
 * APPROVED -> PAID -> IN_PROGRESS -> COMPLETED, plus REJECTED/CANCELLED outcomes.</p>
 */
public enum RepairStatus {

    REQUESTED,
    ACCEPTED,
    INSPECTED,
    QUOTED,
    APPROVED,
    PAID,
    IN_PROGRESS,
    COMPLETED,
    REJECTED,
    CANCELLED

}
