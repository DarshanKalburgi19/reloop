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
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The repair status machine: every allowed {@code from -> to} move. Anything else is refused (409).
 *
 * <ul>
 *   <li>A quote must be approved by the customer before payment: the only way into AWAITING_PAYMENT
 *       is QUOTED -> QUOTE_APPROVED -> AWAITING_PAYMENT.</li>
 *   <li>A rejected quote can't proceed to payment: QUOTE_REJECTED only leads to a revised quote or cancellation.</li>
 *   <li>A revised quote (QUOTED/QUOTE_REJECTED/AWAITING_PAYMENT -> QUOTED) needs a fresh approval.</li>
 *   <li>Only PAID jobs can enter IN_PROGRESS. IN_PROGRESS -> IN_PROGRESS records a progress note.</li>
 *   <li>Cancellation is possible until payment; REJECTED, COMPLETED and CANCELLED are terminal.</li>
 * </ul>
 */
public final class RepairTransitions {

    private static final Map<RepairStatus, Set<RepairStatus>> NEXT = new EnumMap<>(RepairStatus.class);

    static {
        NEXT.put(REQUESTED, EnumSet.of(ACCEPTED, REJECTED, CANCELLED));
        NEXT.put(ACCEPTED, EnumSet.of(INSPECTED, CANCELLED));
        NEXT.put(INSPECTED, EnumSet.of(QUOTED, CANCELLED));
        NEXT.put(QUOTED, EnumSet.of(QUOTED, QUOTE_APPROVED, QUOTE_REJECTED, CANCELLED));
        NEXT.put(QUOTE_APPROVED, EnumSet.of(AWAITING_PAYMENT));
        NEXT.put(QUOTE_REJECTED, EnumSet.of(QUOTED, CANCELLED));
        NEXT.put(AWAITING_PAYMENT, EnumSet.of(PAID, QUOTED, CANCELLED));
        NEXT.put(PAID, EnumSet.of(IN_PROGRESS));
        NEXT.put(IN_PROGRESS, EnumSet.of(IN_PROGRESS, COMPLETED));
        NEXT.put(REJECTED, EnumSet.noneOf(RepairStatus.class));
        NEXT.put(COMPLETED, EnumSet.noneOf(RepairStatus.class));
        NEXT.put(CANCELLED, EnumSet.noneOf(RepairStatus.class));
    }

    private RepairTransitions() {
    }

    public static boolean allowed(RepairStatus from, RepairStatus to) {
        return NEXT.get(from).contains(to);
    }

    public static Set<RepairStatus> next(RepairStatus from) {
        return Collections.unmodifiableSet(NEXT.get(from));
    }
}
