package com.reloop.orderpayment.entity;

import com.reloop.orderpayment.enums.LedgerAccount;
import com.reloop.orderpayment.enums.LedgerDirection;
import com.reloop.orderpayment.enums.LedgerEntryType;
import com.reloop.orderpayment.enums.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * One line of the simulated double-entry ledger (append-only). A SUCCESS payment posts four PAYMENT lines whose
 * debits equal their credits; each refund posts REFUND lines in the opposite direction, linked to the line they
 * reverse. See {@link LedgerAccount}.
 */
@Entity
@Table(name = "ledger_entries", indexes = {
    @Index(name = "ix_ledger_payment", columnList = "payment_transaction_id"),
    @Index(name = "ix_ledger_account_party", columnList = "account, party_account_id"),
    @Index(name = "ix_ledger_refund", columnList = "refund_record_id")
})
@org.hibernate.annotations.Check(name = "ck_ledger_positive", constraints = "amount_paise > 0")
public class LedgerEntry extends BaseEntity {

    @Column(name = "payment_transaction_id", nullable = false)
    private Long paymentTransactionId;

    /** Business transaction id of the payment (TXN-...), for reading the ledger without joins. */
    @Column(name = "transaction_reference", nullable = false, length = 64)
    private String transactionReference;

    @Column(name = "refund_record_id")
    private Long refundRecordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private LedgerEntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(name = "account", nullable = false, length = 40)
    private LedgerAccount account;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private LedgerDirection direction;

    /** Always positive; the direction gives the sign. */
    @Column(name = "amount_paise", nullable = false)
    private Long amountPaise;

    /** Customer account for CUSTOMER_PAYMENTS, provider account for PROVIDER_PAYABLE, null for platform revenue. */
    @Column(name = "party_account_id")
    private Long partyAccountId;

    /** For REFUND lines: the PAYMENT line being reversed. */
    @Column(name = "reversal_of_entry_id")
    private Long reversalOfEntryId;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    public LedgerEntry() {
    }

    /** +amount for credits, −amount for debits. */
    public long signedCreditPaise() {
        return direction == LedgerDirection.CREDIT ? amountPaise : -amountPaise;
    }

    public Long getPaymentTransactionId() {
        return paymentTransactionId;
    }

    public void setPaymentTransactionId(Long paymentTransactionId) {
        this.paymentTransactionId = paymentTransactionId;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public Long getRefundRecordId() {
        return refundRecordId;
    }

    public void setRefundRecordId(Long refundRecordId) {
        this.refundRecordId = refundRecordId;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public LedgerEntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(LedgerEntryType entryType) {
        this.entryType = entryType;
    }

    public LedgerAccount getAccount() {
        return account;
    }

    public void setAccount(LedgerAccount account) {
        this.account = account;
    }

    public LedgerDirection getDirection() {
        return direction;
    }

    public void setDirection(LedgerDirection direction) {
        this.direction = direction;
    }

    public Long getAmountPaise() {
        return amountPaise;
    }

    public void setAmountPaise(Long amountPaise) {
        this.amountPaise = amountPaise;
    }

    public Long getPartyAccountId() {
        return partyAccountId;
    }

    public void setPartyAccountId(Long partyAccountId) {
        this.partyAccountId = partyAccountId;
    }

    public Long getReversalOfEntryId() {
        return reversalOfEntryId;
    }

    public void setReversalOfEntryId(Long reversalOfEntryId) {
        this.reversalOfEntryId = reversalOfEntryId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
