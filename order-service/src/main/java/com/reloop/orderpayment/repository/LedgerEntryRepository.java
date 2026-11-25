package com.reloop.orderpayment.repository;

import com.reloop.orderpayment.entity.LedgerEntry;
import com.reloop.orderpayment.enums.LedgerAccount;
import com.reloop.orderpayment.enums.LedgerEntryType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Append-only simulated ledger. */
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByPaymentTransactionIdOrderByIdAsc(Long paymentTransactionId);

    List<LedgerEntry> findByPaymentTransactionIdAndEntryTypeOrderByIdAsc(Long paymentTransactionId, LedgerEntryType entryType);

    List<LedgerEntry> findByAccountAndPartyAccountIdOrderByIdAsc(LedgerAccount account, Long partyAccountId);
}
