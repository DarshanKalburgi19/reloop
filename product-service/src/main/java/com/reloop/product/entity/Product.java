package com.reloop.product.entity;

import com.reloop.product.enums.ProductCategory;
import com.reloop.product.enums.ProductCondition;
import com.reloop.product.enums.ProductStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A second-hand listing on the marketplace.
 *
 * <p>Money is stored as <b>integer paise</b> (₹1 = 100 paise) so we never use
 * floating point for prices. External buyers/sellers are plain account IDs
 * owned by this same service, so a real FK is used here.</p>
 */
@Entity
@Table(name = "products", indexes = {
    @Index(name = "ix_products_seller_status", columnList = "seller_account_id, status"),
    @Index(name = "ix_products_title", columnList = "title"),
    @Index(name = "ix_products_category", columnList = "category"),
    @Index(name = "ix_products_store", columnList = "store_id"),
    @Index(name = "ix_products_status_created", columnList = "status, created_at"),
    @Index(name = "ix_products_city", columnList = "location_city"),
    @Index(name = "ix_products_price", columnList = "price_paise"),
    @Index(name = "ix_products_passport", columnList = "passport_id")
})
public class Product extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_account_id", nullable = false, foreignKey = @ForeignKey(name = "fk_products_seller"))
    private Account seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", foreignKey = @ForeignKey(name = "fk_products_store"))
    private Store store;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private ProductCategory category;

    /** Price in paise (₹123.45 = 12345). Never a double/float. */
    @Column(name = "price_paise", nullable = false)
    private Long pricePaise;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductStatus status = ProductStatus.DRAFT;

    @Column(name = "location_city", length = 120)
    private String locationCity;

    @Column(name = "sold_at")
    private LocalDateTime soldAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition", nullable = false, length = 20)
    private ProductCondition condition;

    /** Free-text pickup point when the item is not sold from a store (e.g. "Navrangpura, near Swastik Cross Roads"). */
    @Column(name = "pickup_location", length = 255)
    private String pickupLocation;

    /** The physical item's passport (shared by every listing of the same item). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "passport_id", nullable = false, foreignKey = @ForeignKey(name = "fk_products_passport"))
    private ProductPassport passport;

    /** Set when the listing is sold. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_account_id", foreignKey = @ForeignKey(name = "fk_products_buyer"))
    private Account buyer;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ProductImage> images = new ArrayList<>();

    public Product() {
    }

    public Account getSeller() {
        return seller;
    }

    public void setSeller(Account seller) {
        this.seller = seller;
    }

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public void setCategory(ProductCategory category) {
        this.category = category;
    }

    public Long getPricePaise() {
        return pricePaise;
    }

    public void setPricePaise(Long pricePaise) {
        this.pricePaise = pricePaise;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public void setStatus(ProductStatus status) {
        this.status = status;
    }

    public String getLocationCity() {
        return locationCity;
    }

    public void setLocationCity(String locationCity) {
        this.locationCity = locationCity;
    }

    public LocalDateTime getSoldAt() {
        return soldAt;
    }

    public void setSoldAt(LocalDateTime soldAt) {
        this.soldAt = soldAt;
    }

    public List<ProductImage> getImages() {
        return images;
    }

    public void setImages(List<ProductImage> images) {
        this.images = images;
    }

    public ProductCondition getCondition() {
        return condition;
    }

    public void setCondition(ProductCondition condition) {
        this.condition = condition;
    }

    public String getPickupLocation() {
        return pickupLocation;
    }

    public void setPickupLocation(String pickupLocation) {
        this.pickupLocation = pickupLocation;
    }

    public ProductPassport getPassport() {
        return passport;
    }

    public void setPassport(ProductPassport passport) {
        this.passport = passport;
    }

    public Account getBuyer() {
        return buyer;
    }

    public void setBuyer(Account buyer) {
        this.buyer = buyer;
    }

    /** Replaces all images; keeps the bidirectional link consistent (orphanRemoval deletes old rows). */
    public void replaceImages(List<ProductImage> newImages) {
        images.clear();
        for (ProductImage image : newImages) {
            image.setProduct(this);
            images.add(image);
        }
    }
}
