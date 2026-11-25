package com.reloop.orderpayment.entity;

import com.reloop.orderpayment.enums.CommissionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;

/**
 * The platform's double-entry commission ledger: ONE row per SUCCESSFUL
 * payment recording both fees separately, with the rates frozen at that
 * moment.
 *
 * <p>Never compute ReLoop revenue by summing payments - always read this
 * table (and reverse rows on refunds via {@code REVERSED}).</p>
 */
@Entity
@Table(
    name = "commission_records",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_commission_payment", columnNames = "payment_transaction_id"),
        @UniqueConstraint(name = "uk_commission_transaction_id", columnNames = "transaction_id")
    },
    indexes = @Index(name = "ix_commission_status", columnList = "status")
)
public class CommissionRecord extends BaseEntity {

    /** Same-service FK to the successful payment that generated this row. */
    @Column(name = "payment_transaction_id", nullable = false)
    private Long paymentTransactionId;

    /** Business transaction ID copy for easy lookup/reporting. */
    @Column(name = "transaction_id", nullable = false, length = 64)
    private String transactionId;

    /** PURCHASE_ORDER or REPAIR_QUOTE - stored as string, no enum coupling. */
    @Column(name = "reference_type", nullable = false, length = 30)
    private String referenceType;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column(name = "customer_fee_rate", nullable = false, precision = 8, scale = 6)
    private BigDecimal customerFeeRate;

    @Column(name = "customer_fee_paise", nullable = false)
    private Long customerFeePaise;

    @Column(name = "provider_commission_rate", nullable = false, precision = 8, scale = 6)
    private BigDecimal providerCommissionRate;

    @Column(name = "provider_commission_paise", nullable = false)
    private Long providerCommissionPaise;

    /** customer fee + provider commission = ReLoop's total take. */
    @Column(name = "total_commission_paise", nullable = false)
    private Long totalCommissionPaise;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CommissionStatus status = CommissionStatus.RECORDED;

    public CommissionRecord() {
    }

    public Long getPaymentTransactionId() {
        return paymentTransactionId;
    }

    public void setPaymentTransactionId(Long paymentTransactionId) {
        this.paymentTransactionId = paymentTransactionId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(Long referenceId) {
        this.referenceId = referenceId;
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

    public BigDecimal getProviderCommissionRate() {
        return providerCommissionRate;
    }

    public void setProviderCommissionRate(BigDecimal providerCommissionRate) {
        this.providerCommissionRate = providerCommissionRate;
    }

    public Long getProviderCommissionPaise() {
        return providerCommissionPaise;
    }

    public void setProviderCommissionPaise(Long providerCommissionPaise) {
        this.providerCommissionPaise = providerCommissionPaise;
    }

    public Long getTotalCommissionPaise() {
        return totalCommissionPaise;
    }

    public void setTotalCommissionPaise(Long totalCommissionPaise) {
        this.totalCommissionPaise = totalCommissionPaise;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public CommissionStatus getStatus() {
        return status;
    }

    public void setStatus(CommissionStatus status) {
        this.status = status;
    }
}
