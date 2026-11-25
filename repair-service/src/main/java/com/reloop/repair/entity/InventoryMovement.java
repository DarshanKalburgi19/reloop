package com.reloop.repair.entity;

import com.reloop.repair.enums.InventoryMovementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Append-only stock ledger. Every quantity change of a {@link SparePart} is one row.
 *
 * <p>Model fields (API names): {@code id, repairerId, sparePartId, repairRequestId, movementType, quantity,
 * movementDate}.</p>
 *
 * <ul>
 *   <li>{@code quantityChange} is signed (+ in, − out); the API's {@code quantity} is its absolute value.</li>
 *   <li>{@code stockAfter} is the running balance after this movement (audit snapshot).</li>
 *   <li>{@code unitCostPaise} is the unit cost at the time: the purchase price for STOCK_IN, otherwise the
 *       part's cost then. It values parts used in repairs without depending on later price changes.</li>
 * </ul>
 */
@Entity
@Table(name = "inventory_movements", indexes = {
    @Index(name = "ix_movements_part_time", columnList = "spare_part_id, created_at"),
    @Index(name = "ix_movements_profile_date", columnList = "repairer_profile_id, movement_date"),
    @Index(name = "ix_movements_request", columnList = "repair_request_id"),
    @Index(name = "ix_movements_type", columnList = "movement_type")
})
@org.hibernate.annotations.Check(
    name = "ck_movements_valid",
    constraints = "quantity_change <> 0 AND stock_after >= 0 AND unit_cost_paise >= 0"
)
public class InventoryMovement extends BaseEntity {

    /** Owner of the stock ({@code repairerId} in the API); always the part's owner. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repairer_profile_id", nullable = false, foreignKey = @ForeignKey(name = "fk_movements_profile"))
    private RepairerProfile repairerProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spare_part_id", nullable = false, foreignKey = @ForeignKey(name = "fk_movements_part"))
    private SparePart sparePart;

    /** Set for USED_IN_REPAIR. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repair_request_id", foreignKey = @ForeignKey(name = "fk_movements_request"))
    private RepairRequest repairRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    private InventoryMovementType movementType;

    @Column(name = "quantity_change", nullable = false)
    private int quantityChange;

    @Column(name = "stock_after", nullable = false)
    private int stockAfter;

    @Column(name = "unit_cost_paise", nullable = false)
    private Long unitCostPaise = 0L;

    @Column(name = "note", length = 500)
    private String note;

    /** When the stock physically moved (may be earlier than {@code createdAt} for back-dated receipts). */
    @Column(name = "movement_date", nullable = false)
    private LocalDateTime movementDate;

    public InventoryMovement() {
    }

    /** Runs after BaseEntity's callback (superclass callbacks first). */
    @PrePersist
    void defaultMovementDate() {
        if (movementDate == null) {
            movementDate = getCreatedAt() != null ? getCreatedAt() : LocalDateTime.now();
        }
    }

    /** Units moved (always positive). */
    public int getQuantity() {
        return Math.abs(quantityChange);
    }

    /** Cost of the units moved, in paise. */
    public long totalCostPaise() {
        return Math.multiplyExact((long) getQuantity(), unitCostPaise == null ? 0L : unitCostPaise);
    }

    public Long getRepairerId() {
        return repairerProfile == null ? null : repairerProfile.getId();
    }

    public RepairerProfile getRepairerProfile() {
        return repairerProfile;
    }

    public void setRepairerProfile(RepairerProfile repairerProfile) {
        this.repairerProfile = repairerProfile;
    }

    public SparePart getSparePart() {
        return sparePart;
    }

    public void setSparePart(SparePart sparePart) {
        this.sparePart = sparePart;
    }

    public RepairRequest getRepairRequest() {
        return repairRequest;
    }

    public void setRepairRequest(RepairRequest repairRequest) {
        this.repairRequest = repairRequest;
    }

    public InventoryMovementType getMovementType() {
        return movementType;
    }

    public void setMovementType(InventoryMovementType movementType) {
        this.movementType = movementType;
    }

    public int getQuantityChange() {
        return quantityChange;
    }

    public void setQuantityChange(int quantityChange) {
        this.quantityChange = quantityChange;
    }

    public int getStockAfter() {
        return stockAfter;
    }

    public void setStockAfter(int stockAfter) {
        this.stockAfter = stockAfter;
    }

    public Long getUnitCostPaise() {
        return unitCostPaise;
    }

    public void setUnitCostPaise(Long unitCostPaise) {
        this.unitCostPaise = unitCostPaise;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getMovementDate() {
        return movementDate;
    }

    public void setMovementDate(LocalDateTime movementDate) {
        this.movementDate = movementDate;
    }
}
