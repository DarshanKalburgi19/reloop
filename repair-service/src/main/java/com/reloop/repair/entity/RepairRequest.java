package com.reloop.repair.entity;

import com.reloop.repair.enums.DeliveryMode;
import com.reloop.repair.enums.RepairServiceCategory;
import com.reloop.repair.enums.RepairPaymentStatus;
import com.reloop.repair.enums.RepairStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The repair job itself, moving through
 * REQUESTED → ACCEPTED → INSPECTED → QUOTED → QUOTE_APPROVED → AWAITING_PAYMENT → PAID →
 * IN_PROGRESS → COMPLETED (plus REJECTED, QUOTE_REJECTED, CANCELLED). See {@code RepairTransitions}.
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

    /** What the item is, e.g. "Samsung Galaxy M31, 2 years old". */
    @Column(name = "item_description", length = 2000)
    private String itemDescription;

    /** What is wrong with it, in the customer's words. */
    @Column(name = "problem_description", length = 2000)
    private String problemDescription;

    /** Demo/placeholder image URLs ({@code /demo-images/...} or https), in display order. No file hosting yet. */
    @ElementCollection
    @CollectionTable(name = "repair_request_images",
            joinColumns = @JoinColumn(name = "repair_request_id"),
            foreignKey = @ForeignKey(name = "fk_request_images_request"))
    @OrderColumn(name = "position")
    @Column(name = "image_url", nullable = false, length = 500)
    private List<String> imageUrls = new ArrayList<>();

    /** Repairer's findings after inspecting the item. */
    @Column(name = "inspection_notes", length = 2000)
    private String inspectionNotes;

    /** Why the request was rejected or cancelled. */
    @Column(name = "close_reason", length = 1000)
    private String closeReason;

    /** Copy of the current quote from order-service (null until the first quote). */
    @Embedded
    private QuoteSnapshot quote;

    /** Optimistic lock: two simultaneous transitions on the same request -> one gets 409. */
    @Version
    @Column(name = "version")
    private Long version;

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

    /** Public payment reference (TXN-…) of the successful payment; the id the receipts and dashboards use. */
    @Column(name = "payment_reference", length = 64)
    private String paymentReference;

    /** Latest payment state reported by order-service; null until the first attempt. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 20)
    private RepairPaymentStatus paymentStatus;

    /** Total refunded on the payment (paise), as reported by order-service; null = nothing refunded. */
    @Column(name = "refunded_amount_paise")
    private Long refundedAmountPaise;

    /** Why the latest attempt failed / was cancelled; cleared once a payment succeeds. */
    @Column(name = "last_payment_failure", length = 500)
    private String lastPaymentFailure;

    /* ── Event timestamps for the timeline ─────────────────────────────── */

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "inspected_at")
    private LocalDateTime inspectedAt;

    @Column(name = "quoted_at")
    private LocalDateTime quotedAt;

    /** When the customer approved the current quote. */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "in_progress_at")
    private LocalDateTime inProgressAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** When the request ended as REJECTED or CANCELLED. */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

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

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public RepairPaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(RepairPaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public long getRefundedAmountPaise() {
        return refundedAmountPaise == null ? 0L : refundedAmountPaise;
    }

    public void setRefundedAmountPaise(long refundedAmountPaise) {
        this.refundedAmountPaise = refundedAmountPaise;
    }

    public String getLastPaymentFailure() {
        return lastPaymentFailure;
    }

    public void setLastPaymentFailure(String lastPaymentFailure) {
        this.lastPaymentFailure = lastPaymentFailure;
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

    public String getProblemDescription() {
        return problemDescription;
    }

    public void setProblemDescription(String problemDescription) {
        this.problemDescription = problemDescription;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public String getInspectionNotes() {
        return inspectionNotes;
    }

    public void setInspectionNotes(String inspectionNotes) {
        this.inspectionNotes = inspectionNotes;
    }

    public String getCloseReason() {
        return closeReason;
    }

    public void setCloseReason(String closeReason) {
        this.closeReason = closeReason;
    }

    public QuoteSnapshot getQuote() {
        return quote;
    }

    public void setQuote(QuoteSnapshot quote) {
        this.quote = quote;
    }

    public Long getVersion() {
        return version;
    }

    public LocalDateTime getInspectedAt() {
        return inspectedAt;
    }

    public void setInspectedAt(LocalDateTime inspectedAt) {
        this.inspectedAt = inspectedAt;
    }

    public LocalDateTime getQuotedAt() {
        return quotedAt;
    }

    public void setQuotedAt(LocalDateTime quotedAt) {
        this.quotedAt = quotedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
