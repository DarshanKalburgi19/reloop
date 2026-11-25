package com.reloop.repair.service;

import com.reloop.repair.dto.inventory.InventoryMovementResponse;
import com.reloop.repair.dto.inventory.SparePartResponse;
import com.reloop.repair.entity.InventoryMovement;
import com.reloop.repair.entity.RepairRequest;
import com.reloop.repair.entity.SparePart;
import com.reloop.repair.enums.SparePartStatus;

/** Entity → DTO mapping for the inventory API. Call inside a transaction (lazy associations). */
public final class InventoryMapper {

    private InventoryMapper() {
    }

    public static SparePartResponse toPart(SparePart p) {
        long unitCost = p.getUnitCostPaise() == null ? 0L : p.getUnitCostPaise();
        long value = p.stockValuePaise();
        return new SparePartResponse(
                p.getId(),
                p.getRepairerId(),
                p.getSku(),
                p.getPartName(),
                p.getCategory(),
                p.getDescription(),
                p.getQuantity(),
                unitCost,
                Money.toRupees(unitCost),
                Money.format(unitCost),
                p.getLowStockThreshold(),
                p.stockStatus(),
                p.isLowStock(),
                value,
                Money.toRupees(value),
                p.getStatus(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }

    public static InventoryMovementResponse toMovement(InventoryMovement m) {
        SparePart part = m.getSparePart();
        RepairRequest rr = m.getRepairRequest();
        long unitCost = m.getUnitCostPaise() == null ? 0L : m.getUnitCostPaise();
        long total = m.totalCostPaise();
        return new InventoryMovementResponse(
                m.getId(),
                m.getRepairerId(),
                part.getId(),
                part.getPartName(),
                part.getSku(),
                rr == null ? null : rr.getId(),
                rr == null ? null : rr.getRequestNumber(),
                m.getMovementType(),
                m.getQuantity(),
                m.getQuantityChange(),
                m.getStockAfter(),
                unitCost,
                total,
                Money.toRupees(total),
                m.getNote(),
                m.getMovementDate(),
                m.getCreatedAt());
    }

    /** True when an ACTIVE part is at or below its threshold (discontinued parts never raise alerts). */
    public static boolean raisesAlert(SparePart p) {
        return p.getStatus() == SparePartStatus.ACTIVE && p.isLowStock();
    }

    /** "'Battery' is out of stock" / "'Battery' is low on stock: 2 left (threshold 5)" / null. */
    public static String alertMessage(SparePart p) {
        if (!raisesAlert(p)) {
            return null;
        }
        if (p.getQuantity() == 0) {
            return "'" + p.getPartName() + "' is out of stock";
        }
        return "'" + p.getPartName() + "' is low on stock: " + p.getQuantity() + " left (threshold "
                + p.getLowStockThreshold() + ")";
    }
}
