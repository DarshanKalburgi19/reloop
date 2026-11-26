package com.reloop.repair.entity;

import com.reloop.repair.enums.PaymentEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Payment events received from order-service, one row per {@code eventId}. The unique event id makes delivery
 * idempotent: a retried or duplicated call is recognised and changes nothing.
 */
@Entity
@Table(name = "repair_payment_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_repair_payment_event", columnNames = "event_id"),
        indexes = @Index(name = "ix_payment_event_request", columnList = "repair_request_id"))
public class RepairPaymentEvent extends BaseEntity {

    @Column(name = "event_id", nullable = false, length = 100, updatable = false)
    private String eventId;

    @Column(name = "repair_request_id", nullable = false, updatable = false)
    private Long repairRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20, updatable = false)
    private PaymentEventType type;

    @Column(name = "transaction_id", nullable = false, length = 64, updatable = false)
    private String transactionId;

    @Column(name = "amount_paise", updatable = false)
    private Long amountPaise;

    @Column(name = "refunded_amount_paise", updatable = false)
    private Long refundedAmountPaise;

    protected RepairPaymentEvent() {
    }

    public RepairPaymentEvent(String eventId, Long repairRequestId, PaymentEventType type, String transactionId,
                              Long amountPaise, Long refundedAmountPaise) {
        this.eventId = eventId;
        this.repairRequestId = repairRequestId;
        this.type = type;
        this.transactionId = transactionId;
        this.amountPaise = amountPaise;
        this.refundedAmountPaise = refundedAmountPaise;
    }

    public String getEventId() {
        return eventId;
    }

    public Long getRepairRequestId() {
        return repairRequestId;
    }

    public PaymentEventType getType() {
        return type;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public Long getAmountPaise() {
        return amountPaise;
    }

    public Long getRefundedAmountPaise() {
        return refundedAmountPaise;
    }
}
