package com.reloop.product.entity;

import com.reloop.product.enums.ProductImageStatus;
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
 * One image belonging to a product. Multiple rows ordered by {@code sortOrder};
 * exactly one should be primary.
 */
@Entity
@Table(name = "product_images", indexes = {
    @Index(name = "ix_product_images_product", columnList = "product_id, sort_order")
})
public class ProductImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_product_images_product"))
    private Product product;

    /** Relative path or URL of the stored image. */
    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    @Column(name = "alt_text", length = 300)
    private String altText;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductImageStatus status = ProductImageStatus.ACTIVE;

    public ProductImage() {
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getAltText() {
        return altText;
    }

    public void setAltText(String altText) {
        this.altText = altText;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isPrimary() {
        return isPrimary;
    }

    public void setPrimary(boolean primary) {
        isPrimary = primary;
    }

    public ProductImageStatus getStatus() {
        return status;
    }

    public void setStatus(ProductImageStatus status) {
        this.status = status;
    }
}
