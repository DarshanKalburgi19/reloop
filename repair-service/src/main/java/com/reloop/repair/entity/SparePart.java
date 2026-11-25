package com.reloop.repair.entity;

import com.reloop.repair.enums.SparePartCategory;
import com.reloop.repair.enums.SparePartStatus;
import com.reloop.repair.enums.StockStatus;
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
 * A spare part in ONE repairer's inventory.
 *
 * <p>Model fields (API names): {@code id, repairerId, partName, category, quantity, unitCost,
 * lowStockThreshold, createdAt, updatedAt}. The physical column names from the original schema are kept
 * ({@code name}, {@code stock_quantity}, {@code reorder_level}) so existing databases don't need a column
 * rename.</p>
 *
 * <ul>
 *   <li>The quantity can never go negative: {@link #changeQuantity(int)} refuses it, and the CHECK constraint
 *       {@code ck_parts_stock_nonnegative} backs it up.</li>
 *   <li>The quantity changes only through {@code InventoryService}, which writes an {@link InventoryMovement} for
 *       every change.</li>
 *   <li>{@code unitCostPaise} is the purchase cost per unit (weighted average across receipts). It is the basis
 *       of the inventory value.</li>
 * </ul>
 */
@Entity
@Table(
    name = "spare_parts",
    uniqueConstraints = @UniqueConstraint(name = "uk_parts_profile_sku", columnNames = {"repairer_profile_id", "sku"}),
    indexes = {
        @Index(name = "ix_parts_profile_status", columnList = "repairer_profile_id, status"),
        @Index(name = "ix_parts_profile_category", columnList = "repairer_profile_id, category"),
        @Index(name = "ix_parts_name", columnList = "name")
    }
)
@org.hibernate.annotations.Check(
    name = "ck_parts_stock_nonnegative",
    constraints = "stock_quantity >= 0 AND reorder_level >= 0 AND unit_cost_paise >= 0"
)
public class SparePart extends BaseEntity {

    /** Hard upper bound on units of one part (keeps arithmetic far from int overflow). */
    public static final int MAX_QUANTITY = 1_000_000;

    /** Owner: the repairer's workshop profile ({@code repairerId} in the API). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repairer_profile_id", nullable = false, foreignKey = @ForeignKey(name = "fk_parts_profile"))
    private RepairerProfile repairerProfile;

    /** Stock keeping unit, unique per repairer (e.g. "SCR-IP11-BLK"). Generated when not supplied. */
    @Column(name = "sku", nullable = false, length = 60)
    private String sku;

    @Column(name = "name", nullable = false, length = 150)
    private String partName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private SparePartCategory category = SparePartCategory.OTHER;

    @Column(name = "description", length = 1000)
    private String description;

    /** Purchase cost per unit in paise. */
    @Column(name = "unit_cost_paise", nullable = false)
    private Long unitCostPaise = 0L;

    /** Units on hand. */
    @Column(name = "stock_quantity", nullable = false)
    private int quantity;

    /** Alert when {@code quantity <= lowStockThreshold}. */
    @Column(name = "reorder_level", nullable = false)
    private int lowStockThreshold = 5;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SparePartStatus status = SparePartStatus.ACTIVE;

    public SparePart() {
    }

    /**
     * Applies a signed change and returns the new quantity.
     *
     * @throws IllegalStateException if the result would be negative or above {@link #MAX_QUANTITY}
     */
    public int changeQuantity(int delta) {
        long next = (long) quantity + delta;
        if (next < 0) {
            throw new IllegalStateException("Stock of '" + partName + "' would become negative");
        }
        if (next > MAX_QUANTITY) {
            throw new IllegalStateException("Stock of '" + partName + "' would exceed " + MAX_QUANTITY + " units");
        }
        quantity = (int) next;
        return quantity;
    }

    public StockStatus stockStatus() {
        if (quantity == 0) {
            return StockStatus.OUT_OF_STOCK;
        }
        return quantity <= lowStockThreshold ? StockStatus.LOW_STOCK : StockStatus.IN_STOCK;
    }

    public boolean isLowStock() {
        return quantity <= lowStockThreshold;
    }

    /** Value of the units on hand at purchase cost, in paise. */
    public long stockValuePaise() {
        return Math.multiplyExact((long) quantity, unitCostPaise == null ? 0L : unitCostPaise);
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

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getPartName() {
        return partName;
    }

    public void setPartName(String partName) {
        this.partName = partName;
    }

    public SparePartCategory getCategory() {
        return category;
    }

    public void setCategory(SparePartCategory category) {
        this.category = category;
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

    public int getQuantity() {
        return quantity;
    }

    /** Raw setter for persistence tests and initial creation; services use {@link #changeQuantity(int)}. */
    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getLowStockThreshold() {
        return lowStockThreshold;
    }

    public void setLowStockThreshold(int lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }

    public SparePartStatus getStatus() {
        return status;
    }

    public void setStatus(SparePartStatus status) {
        this.status = status;
    }
}
