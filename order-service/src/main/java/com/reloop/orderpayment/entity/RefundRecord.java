package com.reloop.orderpayment.entity;

import com.reloop.orderpayment.enums.RefundReason;
import com.reloop.orderpayment.enums.RefundStatus;
import com.reloop.orderpayment.enums.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * A SIMULATED refund of (part of) a successful payment. The split always satisfies
 * {@code refundAmount = refundedBase + refundedCustomerFee = providerNetReversed + reversedCommission}, where
 * {@code reversedCommission = refundedCustomerFee + reversedProviderCommission} (ReLoop revenue given back).
 */
@Entity
@Table(
    name = "refund_records",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_refund_reference", columnNames = "refund_reference"),
        @UniqueConstraint(name = "uk_refund_idempotency", columnNames = {"payment_transaction_id", "idempotency_key"})
    },
    indexes = {
        @Index(name = "ix_refunds_payment", columnList = "payment_transaction_id"),
        @Index(name = "ix_refunds_status", columnList = "status")
    }
)
@org.hibernate.annotations.Check(
    name = "ck_refunds_amounts",
    constraints = "refund_amount_paise > 0 AND refunded_base_paise > 0 AND refunded_customer_fee_paise >= 0"
            + " AND reversed_provider_commission_paise >= 0"
)
public class RefundRecord extends BaseEntity {

    /** e.g. RFD-20261125-XXXXXXXXXX */
    @Column(name = "refund_reference", nullable = false, length = 64)
    private String refundReference;

    @Column(name = "payment_transaction_id", nullable = false)
    private Long paymentTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", length = 30)
    private TransactionType transactionType;

    /** Purchase order id for marketplace refunds. */
    @Column(name = "order_id")
    private Long orderId;

    /** Total returned to the customer = refundedBase + refundedCustomerFee. */
    @Column(name = "refund_amount_paise", nullable = false)
    private Long refundAmountPaise;

    @Column(name = "refunded_base_paise", nullable = false)
    private Long refundedBasePaise;

    @Column(name = "refunded_customer_fee_paise", nullable = false)
    private Long refundedCustomerFeePaise;

    @Column(name = "reversed_provider_commission_paise", nullable = false)
    private Long reversedProviderCommissionPaise;

    /** Taken back from the provider's payout = refundedBase − reversedProviderCommission. */
    @Column(name = "provider_net_reversed_paise", nullable = false)
    private Long providerNetReversedPaise;

    /** ReLoop revenue reversed = refundedCustomerFee + reversedProviderCommission. */
    @Column(name = "reversed_commission_paise", nullable = false)
    private Long reversedCommissionPaise;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RefundStatus status = RefundStatus.INITIATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    private RefundReason reason;

    @Column(name = "note", length = 500)
    private String note;

    /** Account that issued the refund (the provider), or null when issued by the platform (internal API). */
    @Column(name = "initiated_by_account_id")
    private Long initiatedByAccountId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /** Client-supplied Idempotency-Key (unique per payment); null when the caller sent none. */
    @Column(name = "idempotency_key", length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public RefundRecord() {
    }

    public String getRefundReference() {
        return refundReference;
    }

    public void setRefundReference(String refundReference) {
        this.refundReference = refundReference;
    }

    public Long getPaymentTransactionId() {
        return paymentTransactionId;
    }

    public void setPaymentTransactionId(Long paymentTransactionId) {
        this.paymentTransactionId = paymentTransactionId;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getRefundAmountPaise() {
        return refundAmountPaise;
    }

    public void setRefundAmountPaise(Long refundAmountPaise) {
        this.refundAmountPaise = refundAmountPaise;
    }

    public Long getRefundedBasePaise() {
        return refundedBasePaise;
    }

    public void setRefundedBasePaise(Long refundedBasePaise) {
        this.refundedBasePaise = refundedBasePaise;
    }

    public Long getRefundedCustomerFeePaise() {
        return refundedCustomerFeePaise;
    }

    public void setRefundedCustomerFeePaise(Long refundedCustomerFeePaise) {
        this.refundedCustomerFeePaise = refundedCustomerFeePaise;
    }

    public Long getReversedProviderCommissionPaise() {
        return reversedProviderCommissionPaise;
    }

    public void setReversedProviderCommissionPaise(Long reversedProviderCommissionPaise) {
        this.reversedProviderCommissionPaise = reversedProviderCommissionPaise;
    }

    public Long getProviderNetReversedPaise() {
        return providerNetReversedPaise;
    }

    public void setProviderNetReversedPaise(Long providerNetReversedPaise) {
        this.providerNetReversedPaise = providerNetReversedPaise;
    }

    public Long getReversedCommissionPaise() {
        return reversedCommissionPaise;
    }

    public void setReversedCommissionPaise(Long reversedCommissionPaise) {
        this.reversedCommissionPaise = reversedCommissionPaise;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public void setStatus(RefundStatus status) {
        this.status = status;
    }

    public RefundReason getReason() {
        return reason;
    }

    public void setReason(RefundReason reason) {
        this.reason = reason;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Long getInitiatedByAccountId() {
        return initiatedByAccountId;
    }

    public void setInitiatedByAccountId(Long initiatedByAccountId) {
        this.initiatedByAccountId = initiatedByAccountId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
