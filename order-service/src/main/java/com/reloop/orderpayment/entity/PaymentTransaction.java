package com.reloop.orderpayment.entity;

import com.reloop.orderpayment.enums.PaymentMethod;
import com.reloop.orderpayment.enums.PaymentStatus;
import com.reloop.orderpayment.enums.ProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ONE SIMULATED payment attempt. Never stores card numbers, CVV, UPI PINs or
 * banking credentials - only method TYPE and outcome.
 *
 * <p>{@code transactionId} is globally unique (requirement 5). Exactly one of
 * {@code purchaseOrderId}/{@code repairQuoteId} must be set (DB check
 * constraint). Both fee rates are frozen here at attempt time.</p>
 */
@Entity
@Table(
    name = "payment_transactions",
    uniqueConstraints = @UniqueConstraint(name = "uk_payments_transaction_id", columnNames = "transaction_id"),
    indexes = {
        @Index(name = "ix_payments_status", columnList = "status"),
        @Index(name = "ix_payments_order", columnList = "purchase_order_id"),
        @Index(name = "ix_payments_quote", columnList = "repair_quote_id"),
        @Index(name = "ix_payments_buyer", columnList = "buyer_account_id"),
        @Index(name = "ix_payments_created", columnList = "created_at")
    }
)
@org.hibernate.annotations.Check(
    name = "ck_payments_exactly_one_ref",
    constraints = "(purchase_order_id IS NULL) <> (repair_quote_id IS NULL)"
)
public class PaymentTransaction extends BaseEntity {

    /** Unique business transaction ID, e.g. TXN-20261125-8F3K2A (never reused). */
    @Column(name = "transaction_id", nullable = false, length = 64)
    private String transactionId;

    /** Nullable: set only when paying a purchase order (same service - real FK). */
    @Column(name = "purchase_order_id")
    private Long purchaseOrderId;

    /** Nullable: set only when paying a repair quote (same service - real FK). */
    @Column(name = "repair_quote_id")
    private Long repairQuoteId;

    /** External ID of product-service Account (no cross-service FK). */
    @Column(name = "buyer_account_id", nullable = false)
    private Long buyerAccountId;

    /** External ID of the provider: seller Account or repairer RepairerProfile. */
    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 20)
    private ProviderType providerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    /** Amount being charged before customer fee, paise. */
    @Column(name = "base_amount_paise", nullable = false)
    private Long baseAmountPaise;

    @Column(name = "customer_fee_rate", nullable = false, precision = 8, scale = 6)
    private BigDecimal customerFeeRate;

    @Column(name = "customer_fee_paise", nullable = false)
    private Long customerFeePaise;

    /** base + customer fee - total debited in the simulation. */
    @Column(name = "total_charged_paise", nullable = false)
    private Long totalChargedPaise;

    @Column(name = "repairer_commission_rate", nullable = false, precision = 8, scale = 6)
    private BigDecimal repairerCommissionRate;

    @Column(name = "repairer_commission_paise", nullable = false)
    private Long repairerCommissionPaise;

    /** base - commission - simulated payout to the provider. */
    @Column(name = "provider_net_paise", nullable = false)
    private Long providerNetPaise;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public PaymentTransaction() {
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public Long getPurchaseOrderId() {
        return purchaseOrderId;
    }

    public void setPurchaseOrderId(Long purchaseOrderId) {
        this.purchaseOrderId = purchaseOrderId;
    }

    public Long getRepairQuoteId() {
        return repairQuoteId;
    }

    public void setRepairQuoteId(Long repairQuoteId) {
        this.repairQuoteId = repairQuoteId;
    }

    public Long getBuyerAccountId() {
        return buyerAccountId;
    }

    public void setBuyerAccountId(Long buyerAccountId) {
        this.buyerAccountId = buyerAccountId;
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

    public PaymentMethod getMethod() {
        return method;
    }

    public void setMethod(PaymentMethod method) {
        this.method = method;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public Long getBaseAmountPaise() {
        return baseAmountPaise;
    }

    public void setBaseAmountPaise(Long baseAmountPaise) {
        this.baseAmountPaise = baseAmountPaise;
    }

    public BigDecimal getCustomerFeeRate() {
        return customerFeeRate;
    }

    public void setCustomerFeeRate(BigDecimal customerFeeRate) {
        this.customerFeeRate = customerFeeRate;
    }

    public Long getCustomerFeePaise() {
        return customerFeePaise;
    }

    public void setCustomerFeePaise(Long customerFeePaise) {
        this.customerFeePaise = customerFeePaise;
    }

    public Long getTotalChargedPaise() {
        return totalChargedPaise;
    }

    public void setTotalChargedPaise(Long totalChargedPaise) {
        this.totalChargedPaise = totalChargedPaise;
    }

    public BigDecimal getRepairerCommissionRate() {
        return repairerCommissionRate;
    }

    public void setRepairerCommissionRate(BigDecimal repairerCommissionRate) {
        this.repairerCommissionRate = repairerCommissionRate;
    }

    public Long getRepairerCommissionPaise() {
        return repairerCommissionPaise;
    }

    public void setRepairerCommissionPaise(Long repairerCommissionPaise) {
        this.repairerCommissionPaise = repairerCommissionPaise;
    }

    public Long getProviderNetPaise() {
        return providerNetPaise;
    }

    public void setProviderNetPaise(Long providerNetPaise) {
        this.providerNetPaise = providerNetPaise;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public LocalDateTime getAttemptedAt() {
        return attemptedAt;
    }

    public void setAttemptedAt(LocalDateTime attemptedAt) {
        this.attemptedAt = attemptedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
