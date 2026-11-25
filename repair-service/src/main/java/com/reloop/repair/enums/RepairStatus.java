package com.reloop.repair.enums;

/**
 * Repair request lifecycle (stored as string, never ordinal). Allowed moves live in
 * {@code service.RepairTransitions}.
 *
 * <pre>
 * REQUESTED ─accept→ ACCEPTED ─inspect→ INSPECTED ─quote→ QUOTED ─approve→ QUOTE_APPROVED ─(system)→ AWAITING_PAYMENT
 *     │ reject                                              │  ▲ reject→ QUOTE_REJECTED ─revise→ QUOTED   │ paid (order-service)
 *     ▼                                                     └──┴──────── revise ───────────────────────────┤
 *  REJECTED                                                                                               PAID ─progress→ IN_PROGRESS ─complete→ COMPLETED
 * CANCELLED: any time before payment (customer or assigned repairer).
 * </pre>
 */
public enum RepairStatus {

    /** Customer submitted the request; waiting for the repairer. */
    REQUESTED,
    /** Repairer accepted the job; the item is brought in / sent / picked up. */
    ACCEPTED,
    /** Repairer declined the request (terminal). */
    REJECTED,
    /** Repairer has the item and recorded inspection findings. */
    INSPECTED,
    /** A quote (first or revised) is waiting for the customer's decision. */
    QUOTED,
    /** Customer approved the current quote (immediately followed by AWAITING_PAYMENT). */
    QUOTE_APPROVED,
    /** Customer rejected the current quote; the repairer may revise it, or either side may cancel. */
    QUOTE_REJECTED,
    /** Approved quote waiting for payment through order-service. */
    AWAITING_PAYMENT,
    /** order-service confirmed payment of the approved quote. */
    PAID,
    /** Repair work under way (progress notes keep this status). */
    IN_PROGRESS,
    /** Repair finished (terminal). */
    COMPLETED,
    /** Withdrawn before payment (terminal). */
    CANCELLED;

    public boolean isTerminal() {
        return this == REJECTED || this == COMPLETED || this == CANCELLED;
    }
}
