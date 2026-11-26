package com.reloop.orderpayment.entity;

import com.reloop.orderpayment.enums.PayoutStatus;
import com.reloop.orderpayment.enums.ProviderType;
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
 * Simulated payout owed to a provider for ONE successful payment (never created for failed or cancelled payments):
 * {@code net = gross − commission}. Refunds record the reversed portions; the provider's current earning from this
 * payment is {@code net − reversedNet}.
 */
@Entity
@Table(
    name = "provider_payouts",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_payout_reference", columnNames = "payout_reference"),
        @UniqueConstraint(name = "uk_payout_payment", columnNames = "payment_transaction_id")
    },
    indexes = {
        @Index(name = "ix_payouts_provider", columnList = "provider_id"),
        @Index(name = "ix_payouts_provider_account", columnList = "provider_account_id"),
        @Index(name = "ix_payouts_status", columnList = "status")
    }
)
public class ProviderPayout extends BaseEntity {

    @Column(name = "payout_reference", nullable = false, length = 64)
    private String payoutReference;

    @Column(name = "payment_transaction_id", nullable = false)
    private Long paymentTransactionId;

    /** Seller Account ID, or RepairerProfile ID for repairs. */
    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 20)
    private ProviderType providerType;

    /** product-service Account ID of the provider (dashboards and access checks use this). */
    @Column(name = "provider_account_id")
    private Long providerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", length = 30)
    private TransactionType transactionType;

    /** Provider's gross amount (the base price). */
    @Column(name = "gross_amount_paise", nullable = false)
    private Long grossAmountPaise;

    /** ReLoop commission deducted from the gross. */
    @Column(name = "commission_paise", nullable = false)
    private Long commissionPaise;

    /** gross − commission. */
    @Column(name = "net_amount_paise", nullable = false)
    private Long netAmountPaise;

    @Column(name = "refunded_gross_paise", nullable = false)
    private Long refundedGrossPaise = 0L;

    @Column(name = "reversed_commission_paise", nullable = false)
    private Long reversedCommissionPaise = 0L;

    /** Net amount taken back by refunds (refundedGross − reversedCommission). */
    @Column(name = "reversed_net_paise", nullable = false)
    private Long reversedNetPaise = 0L;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayoutStatus status = PayoutStatus.PENDING;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    public ProviderPayout() {
    }

    /** What the provider currently earns from this payment. */
    public long currentNetPaise() {
        return netAmountPaise - reversedNetPaise;
    }

    public String getPayoutReference() {
        return payoutReference;
    }

    public void setPayoutReference(String payoutReference) {
        this.payoutReference = payoutReference;
    }

    public Long getPaymentTransactionId() {
        return paymentTransactionId;
    }

    public void setPaymentTransactionId(Long paymentTransactionId) {
        this.paymentTransactionId = paymentTransactionId;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public ProviderType getProviderType() {
        return providerType;
    }

    public void setProviderType(ProviderType providerType) {
        this.providerType = providerType;
    }

    public Long getProviderAccountId() {
        return providerAccountId;
    }

    public void setProviderAccountId(Long providerAccountId) {
        this.providerAccountId = providerAccountId;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public Long getGrossAmountPaise() {
        return grossAmountPaise;
    }

    public void setGrossAmountPaise(Long grossAmountPaise) {
        this.grossAmountPaise = grossAmountPaise;
    }

    public Long getCommissionPaise() {
        return commissionPaise;
    }

    public void setCommissionPaise(Long commissionPaise) {
        this.commissionPaise = commissionPaise;
    }

    public Long getNetAmountPaise() {
        return netAmountPaise;
    }

    public void setNetAmountPaise(Long netAmountPaise) {
        this.netAmountPaise = netAmountPaise;
    }

    public Long getRefundedGrossPaise() {
        return refundedGrossPaise;
    }

    public void setRefundedGrossPaise(Long refundedGrossPaise) {
        this.refundedGrossPaise = refundedGrossPaise;
    }

    public Long getReversedCommissionPaise() {
        return reversedCommissionPaise;
    }

    public void setReversedCommissionPaise(Long reversedCommissionPaise) {
        this.reversedCommissionPaise = reversedCommissionPaise;
    }

    public Long getReversedNetPaise() {
        return reversedNetPaise;
    }

    public void setReversedNetPaise(Long reversedNetPaise) {
        this.reversedNetPaise = reversedNetPaise;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public PayoutStatus getStatus() {
        return status;
    }

    public void setStatus(PayoutStatus status) {
        this.status = status;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }
}
