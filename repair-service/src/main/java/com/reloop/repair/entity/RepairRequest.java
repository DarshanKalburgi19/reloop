package com.reloop.repair.entity;

import com.reloop.repair.enums.DeliveryMode;
import com.reloop.repair.enums.RepairServiceCategory;
import com.reloop.repair.enums.RepairStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * The repair job itself, moving through
 * REQUESTED → ACCEPTED → INSPECTED → QUOTED → APPROVED → PAID → IN_PROGRESS →
 * COMPLETED (plus REJECTED / CANCELLED).
 *
 * <p>Customer account, delivery address, latest quote and payment IDs are
 * EXTERNAL service IDs (plain Longs, no cross-service FKs). Status event
 * timestamps are denormalised for the timeline UI.</p>
 */
@Entity
@Table(
    name = "repair_requests",
    uniqueConstraints = @UniqueConstraint(name = "uk_requests_number", columnNames = "request_number"),
    indexes = {
        @Index(name = "ix_requests_customer", columnList = "customer_account_id"),
        @Index(name = "ix_requests_profile_status", columnList = "repairer_profile_id, status"),
        @Index(name = "ix_requests_status", columnList = "status")
    }
)
public class RepairRequest extends BaseEntity {

    @Column(name = "request_number", nullable = false, length = 40)
    private String requestNumber;

    /** External product-service Account ID of the customer (no FK). */
    @Column(name = "customer_account_id", nullable = false)
    private Long customerAccountId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repairer_profile_id", nullable = false, foreignKey = @ForeignKey(name = "fk_requests_profile"))
    private RepairerProfile repairerProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repair_service_id", foreignKey = @ForeignKey(name = "fk_requests_service"))
    private RepairService repairService;

    /** External product-service Address ID used for pickup/shipping (no FK). */
    @Column(name = "delivery_address_id")
    private Long deliveryAddressId;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_mode", nullable = false, length = 20)
    private DeliveryMode deliveryMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_category", nullable = false, length = 40)
    private RepairServiceCategory itemCategory;

    @Column(name = "item_description", length = 2000)
    private String itemDescription;

    @Column(name = "customer_notes", length = 2000)
    private String customerNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RepairStatus status = RepairStatus.REQUESTED;

    /* ── External references into order-service (no FK across services) ── */

    @Column(name = "latest_quote_id")
    private Long latestQuoteId;

    @Column(name = "payment_transaction_id")
    private Long paymentTransactionId;

    /* ── Event timestamps for the timeline ─────────────────────────────── */

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "in_progress_at")
    private LocalDateTime inProgressAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public RepairRequest() {
    }

    public String getRequestNumber() {
        return requestNumber;
    }

    public void setRequestNumber(String requestNumber) {
        this.requestNumber = requestNumber;
    }

    public Long getCustomerAccountId() {
        return customerAccountId;
    }

    public void setCustomerAccountId(Long customerAccountId) {
        this.customerAccountId = customerAccountId;
    }

    public RepairerProfile getRepairerProfile() {
        return repairerProfile;
    }

    public void setRepairerProfile(RepairerProfile repairerProfile) {
        this.repairerProfile = repairerProfile;
    }

    public RepairService getRepairService() {
        return repairService;
    }

    public void setRepairService(RepairService repairService) {
        this.repairService = repairService;
    }

    public Long getDeliveryAddressId() {
        return deliveryAddressId;
    }

    public void setDeliveryAddressId(Long deliveryAddressId) {
        this.deliveryAddressId = deliveryAddressId;
    }

    public DeliveryMode getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(DeliveryMode deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public RepairServiceCategory getItemCategory() {
        return itemCategory;
    }

    public void setItemCategory(RepairServiceCategory itemCategory) {
        this.itemCategory = itemCategory;
    }

    public String getItemDescription() {
        return itemDescription;
    }

    public void setItemDescription(String itemDescription) {
        this.itemDescription = itemDescription;
    }

    public String getCustomerNotes() {
        return customerNotes;
    }

    public void setCustomerNotes(String customerNotes) {
        this.customerNotes = customerNotes;
    }

    public RepairStatus getStatus() {
        return status;
    }

    public void setStatus(RepairStatus status) {
        this.status = status;
    }

    public Long getLatestQuoteId() {
        return latestQuoteId;
    }

    public void setLatestQuoteId(Long latestQuoteId) {
        this.latestQuoteId = latestQuoteId;
    }

    public Long getPaymentTransactionId() {
        return paymentTransactionId;
    }

    public void setPaymentTransactionId(Long paymentTransactionId) {
        this.paymentTransactionId = paymentTransactionId;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public LocalDateTime getInProgressAt() {
        return inProgressAt;
    }

    public void setInProgressAt(LocalDateTime inProgressAt) {
        this.inProgressAt = inProgressAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
