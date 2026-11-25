package com.reloop.repair.service;

import static com.reloop.repair.enums.RepairStatus.ACCEPTED;
import static com.reloop.repair.enums.RepairStatus.AWAITING_PAYMENT;
import static com.reloop.repair.enums.RepairStatus.CANCELLED;
import static com.reloop.repair.enums.RepairStatus.COMPLETED;
import static com.reloop.repair.enums.RepairStatus.INSPECTED;
import static com.reloop.repair.enums.RepairStatus.IN_PROGRESS;
import static com.reloop.repair.enums.RepairStatus.PAID;
import static com.reloop.repair.enums.RepairStatus.QUOTED;
import static com.reloop.repair.enums.RepairStatus.QUOTE_APPROVED;
import static com.reloop.repair.enums.RepairStatus.QUOTE_REJECTED;
import static com.reloop.repair.enums.RepairStatus.REJECTED;
import static com.reloop.repair.enums.RepairStatus.REQUESTED;

import com.reloop.repair.enums.RepairStatus;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * What each participant can do, and in which statuses. Used both to enforce the rules and to tell the UI
 * which buttons to show ({@code allowedActions}). {@code target} is the status the action moves to.
 */
public enum RepairAction {

    ACCEPT("accept", true, EnumSet.of(REQUESTED), ACCEPTED, null),
    REJECT("reject", true, EnumSet.of(REQUESTED), REJECTED, null),
    INSPECT("inspect", true, EnumSet.of(ACCEPTED), INSPECTED, "Accept the request before inspecting the item"),
    QUOTE("quote", true, EnumSet.of(INSPECTED), QUOTED,
            "The first quote follows the inspection; use /quote/revise to change an existing quote"),
    REVISE_QUOTE("revise-quote", true, EnumSet.of(QUOTED, QUOTE_REJECTED, AWAITING_PAYMENT), QUOTED,
            "Only an unpaid quote can be revised"),
    PROGRESS("progress", true, EnumSet.of(PAID, IN_PROGRESS), IN_PROGRESS,
            "Repair work can start only after the customer has approved and paid the quote"),
    COMPLETE("complete", true, EnumSet.of(IN_PROGRESS), COMPLETED,
            "Only a repair in progress can be completed"),
    REPAIRER_CANCEL("cancel", true, EnumSet.of(ACCEPTED, INSPECTED, QUOTED, QUOTE_REJECTED, AWAITING_PAYMENT),
            CANCELLED, "Repairers reject new requests (/reject) and can't cancel paid repairs"),

    APPROVE_QUOTE("approve-quote", false, EnumSet.of(QUOTED), QUOTE_APPROVED, "There is no quote waiting for your approval"),
    REJECT_QUOTE("reject-quote", false, EnumSet.of(QUOTED), QUOTE_REJECTED, "There is no quote waiting for your decision"),
    /** Performed at order-service ({@code POST /api/payments/simulate}); listed so the UI can show it. */
    PAY("pay", false, EnumSet.of(AWAITING_PAYMENT), PAID, "Only an approved quote can be paid"),
    CUSTOMER_CANCEL("cancel", false,
            EnumSet.of(REQUESTED, ACCEPTED, INSPECTED, QUOTED, QUOTE_REJECTED, AWAITING_PAYMENT), CANCELLED,
            "Paid, finished or closed repairs can't be cancelled");

    private final String label;
    private final boolean byRepairer;
    private final Set<RepairStatus> from;
    private final RepairStatus target;
    private final String hint;

    RepairAction(String label, boolean byRepairer, Set<RepairStatus> from, RepairStatus target, String hint) {
        this.label = label;
        this.byRepairer = byRepairer;
        this.from = from;
        this.target = target;
        this.hint = hint;
    }

    public String label() {
        return label;
    }

    public boolean byRepairer() {
        return byRepairer;
    }

    public Set<RepairStatus> from() {
        return from;
    }

    public RepairStatus target() {
        return target;
    }

    public boolean allowedIn(RepairStatus status) {
        return from.contains(status);
    }

    /** "Can't progress a repair request that is QUOTED: Repair work can start only after ..." */
    public String refusal(RepairStatus status) {
        String base = "Can't " + label + " a repair request that is " + status;
        return hint == null ? base : base + ": " + hint;
    }

    /** Labels of the actions this participant can take now. */
    public static List<String> available(RepairStatus status, boolean repairer) {
        return EnumSet.allOf(RepairAction.class).stream()
                .filter(a -> a.byRepairer == repairer && a.allowedIn(status))
                .map(RepairAction::label)
                .distinct()
                .toList();
    }
}
