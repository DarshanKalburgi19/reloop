package com.reloop.orderpayment.entity;

import com.reloop.orderpayment.enums.RefundReason;
import com.reloop.orderpayment.enums.RefundStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * ONE simulated refund for a cancelled/disputed order. A COMPLETED refund
 * reverses the matching {@link CommissionRecord} (status = REVERSED) so
 * earnings never include refunded money.
 */
@Entity
@Table(
    name = "refund_records",
    uniqueConstraints = @UniqueConstraint(name = "uk_refund_reference", columnNames = "refund_reference"),
    indexes = {
        @Index(name = "ix_refunds_payment", columnList = "payment_transaction_id"),
        @Index(name = "ix_refunds_status", columnList = "status")
    }
)
public class RefundRecord extends BaseEntity {

    @Column(name = "refund_reference", nullable = false, length = 64)
    private String refundReference;

    @Column(name = "payment_transaction_id", nullable = false)
    private Long paymentTransactionId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "refund_amount_paise", nullable = false)
    private Long refundAmountPaise;

    /** How much commission was reversed with this refund (paise). */
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

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

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
}
