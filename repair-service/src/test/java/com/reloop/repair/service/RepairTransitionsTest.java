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
import static org.assertj.core.api.Assertions.assertThat;

import com.reloop.repair.enums.RepairStatus;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The workflow's state machine and the actions built on it. */
class RepairTransitionsTest {

    @Test
    void onlyPaidJobsEnterInProgress() {
        for (RepairStatus from : RepairStatus.values()) {
            boolean expected = from == PAID || from == IN_PROGRESS;
            assertThat(RepairTransitions.allowed(from, IN_PROGRESS)).as(from + " -> IN_PROGRESS").isEqualTo(expected);
        }
    }

    @Test
    void paymentOnlyFollowsAnApprovedQuote() {
        for (RepairStatus from : RepairStatus.values()) {
            assertThat(RepairTransitions.allowed(from, PAID)).as(from + " -> PAID").isEqualTo(from == AWAITING_PAYMENT);
        }
        assertThat(RepairTransitions.allowed(QUOTE_REJECTED, AWAITING_PAYMENT)).isFalse();
        assertThat(RepairTransitions.allowed(QUOTE_REJECTED, PAID)).isFalse();
    }

    @Test
    void approvalIsTheOnlyWayToAwaitingPayment() {
        for (RepairStatus from : RepairStatus.values()) {
            assertThat(RepairTransitions.allowed(from, AWAITING_PAYMENT)).as(from + " -> AWAITING_PAYMENT")
                    .isEqualTo(from == QUOTE_APPROVED);
        }
        assertThat(RepairTransitions.next(QUOTE_APPROVED)).containsExactly(AWAITING_PAYMENT);
    }

    @Test
    void revisedQuoteGoesBackToQuotedForFreshApproval() {
        assertThat(RepairTransitions.allowed(QUOTED, QUOTED)).isTrue();
        assertThat(RepairTransitions.allowed(QUOTE_REJECTED, QUOTED)).isTrue();
        assertThat(RepairTransitions.allowed(AWAITING_PAYMENT, QUOTED)).isTrue();
        assertThat(RepairTransitions.allowed(PAID, QUOTED)).isFalse();
    }

    @Test
    void happyPathIsAllowedStepByStep() {
        RepairStatus[] path = {REQUESTED, ACCEPTED, INSPECTED, QUOTED, QUOTE_APPROVED, AWAITING_PAYMENT, PAID,
                IN_PROGRESS, COMPLETED};
        for (int i = 1; i < path.length; i++) {
            assertThat(RepairTransitions.allowed(path[i - 1], path[i])).as(path[i - 1] + " -> " + path[i]).isTrue();
        }
    }

    @ParameterizedTest
    @EnumSource(value = RepairStatus.class, names = {"REJECTED", "COMPLETED", "CANCELLED"})
    void terminalStatusesGoNowhere(RepairStatus terminal) {
        assertThat(RepairTransitions.next(terminal)).isEmpty();
        assertThat(terminal.isTerminal()).isTrue();
    }

    @Test
    void cannotCancelOncePaid() {
        for (RepairStatus s : EnumSet.of(PAID, IN_PROGRESS, COMPLETED, REJECTED, CANCELLED)) {
            assertThat(RepairTransitions.allowed(s, CANCELLED)).as(s + " -> CANCELLED").isFalse();
        }
    }

    @Test
    void everyActionTargetIsAValidTransitionFromEachOfItsSources() {
        for (RepairAction action : RepairAction.values()) {
            for (RepairStatus from : action.from()) {
                assertThat(RepairTransitions.allowed(from, action.target())).as(action + " from " + from).isTrue();
            }
        }
    }

    @Test
    void availableActionsDependOnViewer() {
        assertThat(RepairAction.available(REQUESTED, true)).containsExactlyInAnyOrder("accept", "reject");
        assertThat(RepairAction.available(REQUESTED, false)).containsExactly("cancel");
        assertThat(RepairAction.available(QUOTED, false)).containsExactlyInAnyOrder("approve-quote", "reject-quote", "cancel");
        assertThat(RepairAction.available(QUOTED, true)).containsExactlyInAnyOrder("revise-quote", "cancel");
        assertThat(RepairAction.available(AWAITING_PAYMENT, false)).containsExactlyInAnyOrder("pay", "cancel");
        assertThat(RepairAction.available(PAID, true)).containsExactly("progress");
        assertThat(RepairAction.available(COMPLETED, true)).isEmpty();
        assertThat(RepairAction.available(COMPLETED, false)).isEmpty();
    }

    @Test
    void refusalExplainsWhy() {
        assertThat(RepairAction.PROGRESS.refusal(QUOTED))
                .isEqualTo("Can't progress a repair request that is QUOTED: "
                        + "Repair work can start only after the customer has approved and paid the quote");
    }
}
