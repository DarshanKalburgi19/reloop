package com.reloop.orderpayment.entity;

import com.reloop.orderpayment.enums.ProviderType;
import com.reloop.orderpayment.enums.PayoutStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * ONE SIMULATED payout to a seller/repairer after a successful payment.
 * Earnings are read from payout rows - never by summing raw payments.
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
        @Index(name = "ix_payouts_status", columnList = "status")
    }
)
public class ProviderPayout extends BaseEntity {

    @Column(name = "payout_reference", nullable = false, length = 64)
    private String payoutReference;

    @Column(name = "payment_transaction_id", nullable = false)
    private Long paymentTransactionId;

    /** External ID: seller Account.id or repairer RepairerProfile.id. */
    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 20)
    private ProviderType providerType;

    /** External account of the provider's login (product-service). */
    @Column(name = "provider_account_id")
    private Long providerAccountId;

    @Column(name = "gross_amount_paise", nullable = false)
    private Long grossAmountPaise;

    @Column(name = "commission_paise", nullable = false)
    private Long commissionPaise;

    /** gross - commission (e.g. 100000 - 2000 = 98000 paise). */
    @Column(name = "net_amount_paise", nullable = false)
    private Long netAmountPaise;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayoutStatus status = PayoutStatus.PENDING;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    public ProviderPayout() {
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
