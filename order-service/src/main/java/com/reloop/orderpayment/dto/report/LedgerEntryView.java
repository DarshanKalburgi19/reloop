package com.reloop.orderpayment.dto.report;

import com.reloop.orderpayment.entity.LedgerEntry;
import java.time.LocalDateTime;

/** One simulated ledger line. */
public record LedgerEntryView(Long id, String entryType, String account, String direction, long amountPaise,
                              Long partyAccountId, Long reversalOfEntryId, String refundReference, String description,
                              LocalDateTime createdAt) {

    public static LedgerEntryView of(LedgerEntry e, String refundReference) {
        return new LedgerEntryView(e.getId(), e.getEntryType().name(), e.getAccount().name(), e.getDirection().name(),
                e.getAmountPaise(), e.getPartyAccountId(), e.getReversalOfEntryId(), refundReference,
                e.getDescription(), e.getCreatedAt());
    }
}
