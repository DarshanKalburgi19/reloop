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
import jakarta.persistence.Table;

/**
 * Append-only stock ledger: every quantity change of a {@link SparePart} is
 * recorded here (received / consumed by a repair / adjustment / returned).
 * {@code stockAfter} is a running audit snapshot.
 */
@Entity
@Table(name = "inventory_movements", indexes = {
    @Index(name = "ix_movements_part_time", columnList = "spare_part_id, created_at"),
    @Index(name = "ix_movements_request", columnList = "repair_request_id"),
    @Index(name = "ix_movements_type", columnList = "movement_type")
})
public class InventoryMovement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spare_part_id", nullable = false, foreignKey = @ForeignKey(name = "fk_movements_part"))
    private SparePart sparePart;

    /** Set when the movement was caused by a repair job; nullable otherwise. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repair_request_id", foreignKey = @ForeignKey(name = "fk_movements_request"))
    private RepairRequest repairRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    private InventoryMovementType movementType;

    /** Signed change: +5 received, -1 consumed, +1 returned, -2 adjustment. */
    @Column(name = "quantity_change", nullable = false)
    private int quantityChange;

    /** Stock level immediately after this movement (audit trail). */
    @Column(name = "stock_after", nullable = false)
    private int stockAfter;

    /** Unit cost snapshot for consumed parts, paise (0 if not applicable). */
    @Column(name = "unit_cost_paise", nullable = false)
    private Long unitCostPaise = 0L;

    @Column(name = "note", length = 500)
    private String note;

    public InventoryMovement() {
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
}
