package com.reloop.orderpayment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One line of a {@link PurchaseOrder}. Prices are snapshots so history stays
 * correct even if the product listing changes later.
 */
@Entity
@Table(name = "order_items", indexes = {
    @Index(name = "ix_order_items_order", columnList = "order_id"),
    @Index(name = "ix_order_items_product_external", columnList = "product_external_id")
})
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_items_order"))
    private PurchaseOrder order;

    /** External ID of product-service Product (no cross-service FK). */
    @Column(name = "product_external_id", nullable = false)
    private Long productExternalId;

    @Column(name = "product_title_snapshot", nullable = false, length = 200)
    private String productTitleSnapshot;

    @Column(name = "unit_price_paise", nullable = false)
    private Long unitPricePaise;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "line_total_paise", nullable = false)
    private Long lineTotalPaise;

    public OrderItem() {
    }

    public PurchaseOrder getOrder() {
        return order;
    }

    public void setOrder(PurchaseOrder order) {
        this.order = order;
    }

    public Long getProductExternalId() {
        return productExternalId;
    }

    public void setProductExternalId(Long productExternalId) {
        this.productExternalId = productExternalId;
    }

    public String getProductTitleSnapshot() {
        return productTitleSnapshot;
    }

    public void setProductTitleSnapshot(String productTitleSnapshot) {
        this.productTitleSnapshot = productTitleSnapshot;
    }

    public Long getUnitPricePaise() {
        return unitPricePaise;
    }

    public void setUnitPricePaise(Long unitPricePaise) {
        this.unitPricePaise = unitPricePaise;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public Long getLineTotalPaise() {
        return lineTotalPaise;
    }

    public void setLineTotalPaise(Long lineTotalPaise) {
        this.lineTotalPaise = lineTotalPaise;
    }
}
