package com.reloop.orderpayment.entity;

import com.reloop.orderpayment.enums.PaymentMethod;
import com.reloop.orderpayment.enums.PaymentStatus;
import com.reloop.orderpayment.enums.ProviderType;
import com.reloop.orderpayment.enums.TransactionType;
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
 * ONE SIMULATED payment attempt. No money moves and no gateway is contacted. Card numbers, CVV, UPI PINs, bank
 * account numbers and other credentials are never accepted or stored: only the method TYPE and the outcome.
 *
 * <ul>
 *   <li>{@code transactionId} (e.g. {@code TXN-20261125-8F3K2A9QXM}) is globally unique.</li>
 *   <li>Exactly one of {@code purchaseOrderId} / {@code repairQuoteId} is set (CHECK constraint), matching
 *       {@code transactionType}.</li>
 *   <li>All amounts are integer paise and both rates are FROZEN copies from the order/quote, so changing the
 *       configured rates never alters an existing transaction.</li>
 *   <li>{@code customerTotal = base + customerFee}; {@code providerNet = base − providerCommission};
 *       {@code platformRevenue = customerFee + providerCommission}.</li>
 *   <li>Duplicate processing is blocked three ways: a row lock on the order/quote, the unique
 *       {@code settlement_key} (set only on SUCCESS, one per order/quote) and the unique
 *       {@code (buyer_account_id, idempotency_key)}.</li>
 * </ul>
 *
 * <p>Java names are provider-neutral; the original column names ({@code repairer_commission_*}) are kept so
 * existing databases need no column rename.</p>
 */
@Entity
@Table(
    name = "payment_transactions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_payments_transaction_id", columnNames = "transaction_id"),
        @UniqueConstraint(name = "uk_payments_settlement_key", columnNames = "settlement_key"),
        @UniqueConstraint(name = "uk_payments_idempotency", columnNames = {"buyer_account_id", "idempotency_key"})
    },
    indexes = {
        @Index(name = "ix_payments_status", columnList = "status"),
        @Index(name = "ix_payments_order", columnList = "purchase_order_id"),
        @Index(name = "ix_payments_quote", columnList = "repair_quote_id"),
        @Index(name = "ix_payments_buyer", columnList = "buyer_account_id"),
        @Index(name = "ix_payments_provider_account", columnList = "provider_account_id"),
        @Index(name = "ix_payments_type", columnList = "transaction_type"),
        @Index(name = "ix_payments_created", columnList = "created_at")
    }
)
@org.hibernate.annotations.Check(
    name = "ck_payments_exactly_one_ref",
    constraints = "(purchase_order_id IS NULL) <> (repair_quote_id IS NULL)"
)
public class PaymentTransaction extends BaseEntity {

    /** Unique business transaction ID, e.g. TXN-20261125-8F3K2A9QXM (never reused). */
    @Column(name = "transaction_id", nullable = false, length = 64)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    /** Set only when paying a marketplace order. */
    @Column(name = "purchase_order_id")
    private Long purchaseOrderId;

    /** Set only when paying a repair quote. */
    @Column(name = "repair_quote_id")
    private Long repairQuoteId;

    /** product-service Account ID of the paying customer (no cross-service FK). */
    @Column(name = "buyer_account_id", nullable = false)
    private Long buyerAccountId;

    /** Provider reference: seller Account ID, or repair-service RepairerProfile ID for repairs. */
    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    /** product-service Account ID of the provider (seller or repairer login); used for dashboards/access. */
    @Column(name = "provider_account_id")
    private Long providerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 20)
    private ProviderType providerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    /** Provider's gross amount (item price / repair price) before fees, paise. */
    @Column(name = "base_amount_paise", nullable = false)
    private Long baseAmountPaise;

    /** Customer fee rate applied (0.020000 = 2%). */
    @Column(name = "customer_fee_rate", nullable = false, precision = 8, scale = 6)
    private BigDecimal customerFeeRate;

    @Column(name = "customer_fee_paise", nullable = false)
    private Long customerFeePaise;

    /** base + customer fee: what the customer pays. */
    @Column(name = "total_charged_paise", nullable = false)
    private Long totalChargedPaise;

    /** Provider commission rate applied (0.020000 = 2%). */
    @Column(name = "repairer_commission_rate", nullable = false, precision = 8, scale = 6)
    private BigDecimal providerCommissionRate;

    @Column(name = "repairer_commission_paise", nullable = false)
    private Long providerCommissionPaise;

    /** base − provider commission: what the provider earns. */
    @Column(name = "provider_net_paise", nullable = false)
    private Long providerNetPaise;

    /** customer fee + provider commission: ReLoop's revenue. */
    @Column(name = "platform_revenue_paise", nullable = false)
    private Long platformRevenuePaise;

    /** Total returned to the customer by simulated refunds (base + fee portions). */
    @Column(name = "refunded_amount_paise", nullable = false)
    private Long refundedAmountPaise = 0L;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    /** "ORDER-12" / "QUOTE-7" on SUCCESS only: the unique constraint allows one successful payment per target. */
    @Column(name = "settlement_key", length = 64)
    private String settlementKey;

    /** Client-supplied Idempotency-Key header; a retry with the same key returns this payment. */
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

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

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
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

    public Long getProviderAccountId() {
        return providerAccountId;
    }

    public void setProviderAccountId(Long providerAccountId) {
        this.providerAccountId = providerAccountId;
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

    public Long getProviderNetPaise() {
        return providerNetPaise;
    }

    public void setProviderNetPaise(Long providerNetPaise) {
        this.providerNetPaise = providerNetPaise;
    }

    public Long getPlatformRevenuePaise() {
        return platformRevenuePaise;
    }

    public void setPlatformRevenuePaise(Long platformRevenuePaise) {
        this.platformRevenuePaise = platformRevenuePaise;
    }

    public Long getRefundedAmountPaise() {
        return refundedAmountPaise;
    }

    public void setRefundedAmountPaise(Long refundedAmountPaise) {
        this.refundedAmountPaise = refundedAmountPaise;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getSettlementKey() {
        return settlementKey;
    }

    public void setSettlementKey(String settlementKey) {
        this.settlementKey = settlementKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
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
