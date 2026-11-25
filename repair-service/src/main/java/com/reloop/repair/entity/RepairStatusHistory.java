package com.reloop.repair.entity;

import com.reloop.repair.enums.RepairStatus;
import com.reloop.repair.enums.StatusChangeActor;
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

/**
 * Append-only audit row for every repair status change - the "Repair History"
 * the blueprint asks for. One row per transition, never updated.
 */
@Entity
@Table(name = "repair_status_history", indexes = {
    @Index(name = "ix_history_request_time", columnList = "repair_request_id, created_at")
})
public class RepairStatusHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repair_request_id", nullable = false, foreignKey = @ForeignKey(name = "fk_history_request"))
    private RepairRequest repairRequest;

    /** Null for the very first (creation) entry. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private RepairStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private RepairStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "changed_by", nullable = false, length = 20)
    private StatusChangeActor changedBy;

    /** External account ID who performed the change (no cross-service FK). */
    @Column(name = "changed_by_account_id")
    private Long changedByAccountId;

    @Column(name = "note", length = 1000)
    private String note;

    public RepairStatusHistory() {
    }

    public RepairRequest getRepairRequest() {
        return repairRequest;
    }

    public void setRepairRequest(RepairRequest repairRequest) {
        this.repairRequest = repairRequest;
    }

    public RepairStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(RepairStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public RepairStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(RepairStatus toStatus) {
        this.toStatus = toStatus;
    }

    public StatusChangeActor getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(StatusChangeActor changedBy) {
        this.changedBy = changedBy;
    }

    public Long getChangedByAccountId() {
        return changedByAccountId;
    }

    public void setChangedByAccountId(Long changedByAccountId) {
        this.changedByAccountId = changedByAccountId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
