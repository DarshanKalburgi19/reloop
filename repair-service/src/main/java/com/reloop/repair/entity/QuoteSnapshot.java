package com.reloop.repair.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDateTime;

/**
 * Copy of the CURRENT quote, as returned by order-service (which owns quotes, fee rates and payments).
 * Kept here so repair request pages don't need a cross-service call; amounts are paise.
 * The quote ID itself is {@link RepairRequest#getLatestQuoteId()}.
 */
@Embeddable
public class QuoteSnapshot {

    @Column(name = "quote_revision")
    private Integer revision;

    @Column(name = "quote_base_amount_paise")
    private Long baseAmountPaise;

    @Column(name = "quote_customer_fee_paise")
    private Long customerFeePaise;

    @Column(name = "quote_total_payable_paise")
    private Long totalPayablePaise;

    @Column(name = "quote_repairer_commission_paise")
    private Long repairerCommissionPaise;

    @Column(name = "quote_repairer_net_paise")
    private Long repairerNetPaise;

    @Column(name = "quote_summary", length = 2000)
    private String summary;

    @Column(name = "quote_estimated_days")
    private Integer estimatedDays;

    @Column(name = "quote_sent_at")
    private LocalDateTime sentAt;

    protected QuoteSnapshot() {
    }

    public QuoteSnapshot(Integer revision, Long baseAmountPaise, Long customerFeePaise, Long totalPayablePaise,
                         Long repairerCommissionPaise, Long repairerNetPaise, String summary, Integer estimatedDays,
                         LocalDateTime sentAt) {
        this.revision = revision;
        this.baseAmountPaise = baseAmountPaise;
        this.customerFeePaise = customerFeePaise;
        this.totalPayablePaise = totalPayablePaise;
        this.repairerCommissionPaise = repairerCommissionPaise;
        this.repairerNetPaise = repairerNetPaise;
        this.summary = summary;
        this.estimatedDays = estimatedDays;
        this.sentAt = sentAt;
    }

    public Integer getRevision() {
        return revision;
    }

    public Long getBaseAmountPaise() {
        return baseAmountPaise;
    }

    public Long getCustomerFeePaise() {
        return customerFeePaise;
    }

    public Long getTotalPayablePaise() {
        return totalPayablePaise;
    }

    public Long getRepairerCommissionPaise() {
        return repairerCommissionPaise;
    }

    public Long getRepairerNetPaise() {
        return repairerNetPaise;
    }

    public String getSummary() {
        return summary;
    }

    public Integer getEstimatedDays() {
        return estimatedDays;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }
}
