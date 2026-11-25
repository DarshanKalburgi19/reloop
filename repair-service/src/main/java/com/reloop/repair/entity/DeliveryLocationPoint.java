package com.reloop.repair.entity;

import com.reloop.repair.enums.DeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

/**
 * Append-only coordinate history of a SIMULATED delivery: one row per position the simulation produced
 * (sequence 0 = booking at the origin, then one per "advance"). Rows are never updated or deleted.
 */
@Entity
@Immutable
@Table(name = "delivery_location_points",
    uniqueConstraints = @UniqueConstraint(name = "uk_delivery_points_sequence", columnNames = {"delivery_id", "sequence_no"}),
    indexes = @Index(name = "ix_delivery_points_delivery", columnList = "delivery_id, sequence_no"))
public class DeliveryLocationPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_delivery_points_delivery"))
    private Delivery delivery;

    @Column(name = "sequence_no", nullable = false, updatable = false)
    private int sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30, updatable = false)
    private DeliveryStatus status;

    @Column(name = "latitude", nullable = false, updatable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false, updatable = false)
    private double longitude;

    @Column(name = "progress_percent", nullable = false, updatable = false)
    private int progressPercent;

    @Column(name = "remaining_km", nullable = false, updatable = false)
    private double remainingKm;

    @Column(name = "note", nullable = false, length = 300, updatable = false)
    private String note;

    @Column(name = "recorded_by_account_id", updatable = false)
    private Long recordedByAccountId;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    protected DeliveryLocationPoint() {
    }

    public DeliveryLocationPoint(Delivery delivery, int sequenceNo, DeliveryStatus status, double latitude,
                                 double longitude, int progressPercent, double remainingKm, String note,
                                 Long recordedByAccountId, LocalDateTime recordedAt) {
        this.delivery = delivery;
        this.sequenceNo = sequenceNo;
        this.status = status;
        this.latitude = latitude;
        this.longitude = longitude;
        this.progressPercent = progressPercent;
        this.remainingKm = remainingKm;
        this.note = note;
        this.recordedByAccountId = recordedByAccountId;
        this.recordedAt = recordedAt;
    }

    public Long getId() {
        return id;
    }

    public Delivery getDelivery() {
        return delivery;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public double getRemainingKm() {
        return remainingKm;
    }

    public String getNote() {
        return note;
    }

    public Long getRecordedByAccountId() {
        return recordedByAccountId;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }
}
