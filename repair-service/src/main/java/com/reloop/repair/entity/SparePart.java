package com.reloop.repair.entity;

import com.reloop.repair.enums.SparePartStatus;
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

/**
 * A spare part SKU in ONE repairer's inventory. Stock can never go negative
 * (CHECK constraint); every change must be explained by an
 * {@link InventoryMovement} row.
 */
@Entity
@Table(
    name = "spare_parts",
    uniqueConstraints = @UniqueConstraint(name = "uk_parts_profile_sku", columnNames = {"repairer_profile_id", "sku"}),
    indexes = {
        @Index(name = "ix_parts_profile_status", columnList = "repairer_profile_id, status"),
        @Index(name = "ix_parts_name", columnList = "name")
    }
)
@org.hibernate.annotations.Check(
    name = "ck_parts_stock_nonnegative",
    constraint = "stock_quantity >= 0"
)
public class SparePart extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repairer_profile_id", nullable = false, foreignKey = @ForeignKey(name = "fk_parts_profile"))
    private RepairerProfile repairerProfile;

    /** Stock keeping unit, unique per repairer (e.g. "SCR-IP11-BLK"). */
    @Column(name = "sku", nullable = false, length = 60)
    private String sku;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    /** What the repairer pays for one unit, paise. */
    @Column(name = "unit_cost_paise", nullable = false)
    private Long unitCostPaise;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(name = "reorder_level", nullable = false)
    private int reorderLevel = 5;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SparePartStatus status = SparePartStatus.ACTIVE;

    public SparePart() {
    }

    public RepairerProfile getRepairerProfile() {
        return repairerProfile;
    }

    public void setRepairerProfile(RepairerProfile repairerProfile) {
        this.repairerProfile = repairerProfile;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getUnitCostPaise() {
        return unitCostPaise;
    }

    public void setUnitCostPaise(Long unitCostPaise) {
        this.unitCostPaise = unitCostPaise;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(int stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public int getReorderLevel() {
        return reorderLevel;
    }

    public void setReorderLevel(int reorderLevel) {
        this.reorderLevel = reorderLevel;
    }

    public SparePartStatus getStatus() {
        return status;
    }

    public void setStatus(SparePartStatus status) {
        this.status = status;
    }
}
