package com.reloop.orderpayment.entity;

import com.reloop.orderpayment.enums.QuoteStatus;
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
 * A quote the repairer sends for one repair request. Carries the FULL fee
 * breakdown so the UI can show the transparent commission split before the
 * customer approves.
 *
 * <p>All repair/customer/repairer IDs are EXTERNAL service IDs (no FKs).
 * {@code quoteRevision} allows revised quotes; unique per repair request.</p>
 */
@Entity
@Table(
    name = "repair_quotes",
    uniqueConstraints = @UniqueConstraint(name = "uk_quotes_request_revision", columnNames = {"repair_request_id", "quote_revision"}),
    indexes = {
        @Index(name = "ix_quotes_request", columnList = "repair_request_id"),
        @Index(name = "ix_quotes_status", columnList = "status"),
        @Index(name = "ix_quotes_repairer", columnList = "repairer_profile_id"),
        @Index(name = "ix_quotes_repairer_account", columnList = "repairer_account_id")
    }
)
public class RepairQuote extends BaseEntity {

    /** External ID of repair-service RepairRequest (no cross-service FK). */
    @Column(name = "repair_request_id", nullable = false)
    private Long repairRequestExternalId;

    /** External ID of repair-service RepairerProfile (no cross-service FK). */
    @Column(name = "repairer_profile_id", nullable = false)
    private Long repairerProfileId;

    /** External ID of product-service Account (no cross-service FK). */
    @Column(name = "customer_account_id", nullable = false)
    private Long customerAccountId;

    /** product-service Account ID of the repairer (login); owner of the payout and earnings. */
    @Column(name = "repairer_account_id")
    private Long repairerAccountId;

    @Column(name = "quote_revision", nullable = false)
    private int quoteRevision = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private QuoteStatus status = QuoteStatus.DRAFT;

    @Column(name = "summary", length = 2000)
    private String summary;

    /** Price of the repair work, paise. */
    @Column(name = "base_amount_paise", nullable = false)
    private Long baseAmountPaise;

    /** Customer fee rate frozen on THIS quote (0.0200 = 2%). */
    @Column(name = "customer_fee_rate", nullable = false, precision = 8, scale = 6)
    private BigDecimal customerFeeRate;

    @Column(name = "customer_fee_paise", nullable = false)
    private Long customerFeePaise;

    /** base + customer fee - what the customer pays. */
    @Column(name = "total_payable_paise", nullable = false)
    private Long totalPayablePaise;

    /** Repairer commission rate frozen on THIS quote (0.0200 = 2%). */
    @Column(name = "repairer_commission_rate", nullable = false, precision = 8, scale = 6)
    private BigDecimal repairerCommissionRate;

    @Column(name = "repairer_commission_paise", nullable = false)
    private Long repairerCommissionPaise;

    /** base - repairer commission - what the repairer earns. */
    @Column(name = "repairer_net_paise", nullable = false)
    private Long repairerNetPaise;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    public RepairQuote() {
    }

    public Long getRepairRequestExternalId() {
        return repairRequestExternalId;
    }

    public void setRepairRequestExternalId(Long repairRequestExternalId) {
        this.repairRequestExternalId = repairRequestExternalId;
    }

    public Long getRepairerProfileId() {
        return repairerProfileId;
    }

    public void setRepairerProfileId(Long repairerProfileId) {
        this.repairerProfileId = repairerProfileId;
    }

    public Long getCustomerAccountId() {
        return customerAccountId;
    }

    public void setCustomerAccountId(Long customerAccountId) {
        this.customerAccountId = customerAccountId;
    }

    public Long getRepairerAccountId() {
        return repairerAccountId;
    }

    public void setRepairerAccountId(Long repairerAccountId) {
        this.repairerAccountId = repairerAccountId;
    }

    public int getQuoteRevision() {
        return quoteRevision;
    }

    public void setQuoteRevision(int quoteRevision) {
        this.quoteRevision = quoteRevision;
    }

    public QuoteStatus getStatus() {
        return status;
    }

    public void setStatus(QuoteStatus status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
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

    public Long getTotalPayablePaise() {
        return totalPayablePaise;
    }

    public void setTotalPayablePaise(Long totalPayablePaise) {
        this.totalPayablePaise = totalPayablePaise;
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

    public Long getRepairerNetPaise() {
        return repairerNetPaise;
    }

    public void setRepairerNetPaise(Long repairerNetPaise) {
        this.repairerNetPaise = repairerNetPaise;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public LocalDateTime getRejectedAt() {
        return rejectedAt;
    }

    public void setRejectedAt(LocalDateTime rejectedAt) {
        this.rejectedAt = rejectedAt;
    }

    public LocalDateTime getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDateTime validUntil) {
        this.validUntil = validUntil;
    }
}
