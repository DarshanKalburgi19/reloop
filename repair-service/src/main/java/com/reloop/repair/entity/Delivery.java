package com.reloop.repair.entity;

import com.reloop.repair.enums.DeliveryStatus;
import com.reloop.repair.enums.DeliveryType;
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
import jakarta.persistence.Version;
import java.time.LocalDateTime;

/**
 * One SIMULATED delivery leg (replaces the unused Phase-1 {@code delivery_tracking} table).
 *
 * <p>Origin and destination are snapshots of predefined Gujarat demo locations ({@code DemoLocations});
 * the current position is interpolated along the straight origin→destination route by the demo "advance"
 * action. No GPS device or courier is involved. Every position ever shown is kept in
 * {@link DeliveryLocationPoint} (append-only).</p>
 *
 * <p>Exactly one of {@code repairRequest} (repair legs) and {@code orderExternalId} (marketplace leg) is set.
 * {@code customerAccountId} / {@code partnerAccountId} are the two parties allowed to see the leg; they are
 * snapshotted at booking so reads need no cross-service call.</p>
 */
@Entity
@Table(name = "deliveries",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_deliveries_tracking_number", columnNames = "tracking_number"),
        @UniqueConstraint(name = "uk_deliveries_request_type", columnNames = {"repair_request_id", "delivery_type"}),
        @UniqueConstraint(name = "uk_deliveries_order_type", columnNames = {"order_external_id", "delivery_type"})
    },
    indexes = {
        @Index(name = "ix_deliveries_customer", columnList = "customer_account_id"),
        @Index(name = "ix_deliveries_partner", columnList = "partner_account_id"),
        @Index(name = "ix_deliveries_status", columnList = "status")
    })
public class Delivery extends BaseEntity {

    @Column(name = "tracking_number", nullable = false, length = 40, updatable = false)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", nullable = false, length = 30, updatable = false)
    private DeliveryType deliveryType;

    /** Set for CUSTOMER_TO_REPAIRER / REPAIRER_TO_CUSTOMER. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repair_request_id", updatable = false, foreignKey = @ForeignKey(name = "fk_deliveries_request"))
    private RepairRequest repairRequest;

    /** order-service {@code purchase_orders.id} for STORE_TO_CUSTOMER (no FK: different service). */
    @Column(name = "order_external_id", updatable = false)
    private Long orderExternalId;

    /** Repair request number or order number, for display. */
    @Column(name = "reference_number", length = 60, updatable = false)
    private String referenceNumber;

    /** Buyer (marketplace) or repair customer. */
    @Column(name = "customer_account_id", nullable = false, updatable = false)
    private Long customerAccountId;

    /** Seller (marketplace) or the repairer owning the assigned workshop. */
    @Column(name = "partner_account_id", nullable = false, updatable = false)
    private Long partnerAccountId;

    @Column(name = "booked_by_account_id", nullable = false, updatable = false)
    private Long bookedByAccountId;

    @Column(name = "origin_code", nullable = false, length = 40, updatable = false)
    private String originCode;

    @Column(name = "origin_label", nullable = false, length = 120, updatable = false)
    private String originLabel;

    @Column(name = "origin_city", nullable = false, length = 80, updatable = false)
    private String originCity;

    @Column(name = "origin_latitude", nullable = false, updatable = false)
    private double originLatitude;

    @Column(name = "origin_longitude", nullable = false, updatable = false)
    private double originLongitude;

    @Column(name = "destination_code", nullable = false, length = 40, updatable = false)
    private String destinationCode;

    @Column(name = "destination_label", nullable = false, length = 120, updatable = false)
    private String destinationLabel;

    @Column(name = "destination_city", nullable = false, length = 80, updatable = false)
    private String destinationCity;

    @Column(name = "destination_latitude", nullable = false, updatable = false)
    private double destinationLatitude;

    @Column(name = "destination_longitude", nullable = false, updatable = false)
    private double destinationLongitude;

    /** Estimated road distance (straight line × road factor), km. */
    @Column(name = "route_distance_km", nullable = false, updatable = false)
    private double routeDistanceKm;

    @Column(name = "current_latitude", nullable = false)
    private double currentLatitude;

    @Column(name = "current_longitude", nullable = false)
    private double currentLongitude;

    /** Index into {@code DeliverySimulator.CHECKPOINTS}; 0 = BOOKED. */
    @Column(name = "simulation_step", nullable = false)
    private int simulationStep;

    @Column(name = "progress_percent", nullable = false)
    private int progressPercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DeliveryStatus status = DeliveryStatus.BOOKED;

    @Column(name = "estimated_arrival_at", nullable = false)
    private LocalDateTime estimatedArrivalAt;

    @Column(name = "picked_up_at")
    private LocalDateTime pickedUpAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "last_advanced_at", nullable = false)
    private LocalDateTime lastAdvancedAt;

    /** Two simultaneous "advance" clicks: one wins, the other gets 409. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public Delivery() {
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public DeliveryType getDeliveryType() {
        return deliveryType;
    }

    public void setDeliveryType(DeliveryType deliveryType) {
        this.deliveryType = deliveryType;
    }

    public RepairRequest getRepairRequest() {
        return repairRequest;
    }

    public void setRepairRequest(RepairRequest repairRequest) {
        this.repairRequest = repairRequest;
    }

    public Long getOrderExternalId() {
        return orderExternalId;
    }

    public void setOrderExternalId(Long orderExternalId) {
        this.orderExternalId = orderExternalId;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }

    public Long getCustomerAccountId() {
        return customerAccountId;
    }

    public void setCustomerAccountId(Long customerAccountId) {
        this.customerAccountId = customerAccountId;
    }

    public Long getPartnerAccountId() {
        return partnerAccountId;
    }

    public void setPartnerAccountId(Long partnerAccountId) {
        this.partnerAccountId = partnerAccountId;
    }

    public Long getBookedByAccountId() {
        return bookedByAccountId;
    }

    public void setBookedByAccountId(Long bookedByAccountId) {
        this.bookedByAccountId = bookedByAccountId;
    }

    public String getOriginCode() {
        return originCode;
    }

    public void setOriginCode(String originCode) {
        this.originCode = originCode;
    }

    public String getOriginLabel() {
        return originLabel;
    }

    public void setOriginLabel(String originLabel) {
        this.originLabel = originLabel;
    }

    public String getOriginCity() {
        return originCity;
    }

    public void setOriginCity(String originCity) {
        this.originCity = originCity;
    }

    public double getOriginLatitude() {
        return originLatitude;
    }

    public void setOriginLatitude(double originLatitude) {
        this.originLatitude = originLatitude;
    }

    public double getOriginLongitude() {
        return originLongitude;
    }

    public void setOriginLongitude(double originLongitude) {
        this.originLongitude = originLongitude;
    }

    public String getDestinationCode() {
        return destinationCode;
    }

    public void setDestinationCode(String destinationCode) {
        this.destinationCode = destinationCode;
    }

    public String getDestinationLabel() {
        return destinationLabel;
    }

    public void setDestinationLabel(String destinationLabel) {
        this.destinationLabel = destinationLabel;
    }

    public String getDestinationCity() {
        return destinationCity;
    }

    public void setDestinationCity(String destinationCity) {
        this.destinationCity = destinationCity;
    }

    public double getDestinationLatitude() {
        return destinationLatitude;
    }

    public void setDestinationLatitude(double destinationLatitude) {
        this.destinationLatitude = destinationLatitude;
    }

    public double getDestinationLongitude() {
        return destinationLongitude;
    }

    public void setDestinationLongitude(double destinationLongitude) {
        this.destinationLongitude = destinationLongitude;
    }

    public double getRouteDistanceKm() {
        return routeDistanceKm;
    }

    public void setRouteDistanceKm(double routeDistanceKm) {
        this.routeDistanceKm = routeDistanceKm;
    }

    public double getCurrentLatitude() {
        return currentLatitude;
    }

    public void setCurrentLatitude(double currentLatitude) {
        this.currentLatitude = currentLatitude;
    }

    public double getCurrentLongitude() {
        return currentLongitude;
    }

    public void setCurrentLongitude(double currentLongitude) {
        this.currentLongitude = currentLongitude;
    }

    public int getSimulationStep() {
        return simulationStep;
    }

    public void setSimulationStep(int simulationStep) {
        this.simulationStep = simulationStep;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(int progressPercent) {
        this.progressPercent = progressPercent;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public void setStatus(DeliveryStatus status) {
        this.status = status;
    }

    public LocalDateTime getEstimatedArrivalAt() {
        return estimatedArrivalAt;
    }

    public void setEstimatedArrivalAt(LocalDateTime estimatedArrivalAt) {
        this.estimatedArrivalAt = estimatedArrivalAt;
    }

    public LocalDateTime getPickedUpAt() {
        return pickedUpAt;
    }

    public void setPickedUpAt(LocalDateTime pickedUpAt) {
        this.pickedUpAt = pickedUpAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public LocalDateTime getLastAdvancedAt() {
        return lastAdvancedAt;
    }

    public void setLastAdvancedAt(LocalDateTime lastAdvancedAt) {
        this.lastAdvancedAt = lastAdvancedAt;
    }

    public Long getVersion() {
        return version;
    }

    /** True if {@code accountId} is one of the two parties of this leg. */
    public boolean isParty(Long accountId) {
        return accountId != null && (accountId.equals(customerAccountId) || accountId.equals(partnerAccountId));
    }
}
