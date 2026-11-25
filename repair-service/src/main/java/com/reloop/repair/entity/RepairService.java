package com.reloop.repair.entity;

import com.reloop.repair.enums.PriceType;
import com.reloop.repair.enums.RepairServiceCategory;
import com.reloop.repair.enums.RepairServiceStatus;
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
import org.hibernate.annotations.ColumnDefault;

/**
 * One service a repairer offers ("Phone screen replacement - ₹499").
 * Estimate prices are paise; they only pre-fill the quote, the actual quote
 * is created in order-service after inspection.
 */
@Entity
@Table(name = "repair_services", indexes = {
    @Index(name = "ix_services_profile", columnList = "repairer_profile_id, status"),
    @Index(name = "ix_services_category", columnList = "category")
})
public class RepairService extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repairer_profile_id", nullable = false, foreignKey = @ForeignKey(name = "fk_services_profile"))
    private RepairerProfile repairerProfile;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private RepairServiceCategory category;

    /** Price in paise (nullable at DB level; always set by the workshop profile API). */
    @Column(name = "estimated_price_paise")
    private Long estimatedPricePaise;

    /** Whether {@code estimatedPricePaise} is an exact price or a "starting from" price. */
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'STARTING_FROM'")
    @Column(name = "price_type", nullable = false, length = 20)
    private PriceType priceType = PriceType.STARTING_FROM;

    @Column(name = "estimated_days")
    private Integer estimatedDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RepairServiceStatus status = RepairServiceStatus.ACTIVE;

    public RepairService() {
    }

    public RepairerProfile getRepairerProfile() {
        return repairerProfile;
    }

    public void setRepairerProfile(RepairerProfile repairerProfile) {
        this.repairerProfile = repairerProfile;
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

    public RepairServiceCategory getCategory() {
        return category;
    }

    public void setCategory(RepairServiceCategory category) {
        this.category = category;
    }

    public Long getEstimatedPricePaise() {
        return estimatedPricePaise;
    }

    public void setEstimatedPricePaise(Long estimatedPricePaise) {
        this.estimatedPricePaise = estimatedPricePaise;
    }

    public PriceType getPriceType() {
        return priceType;
    }

    public void setPriceType(PriceType priceType) {
        this.priceType = priceType;
    }

    public Integer getEstimatedDays() {
        return estimatedDays;
    }

    public void setEstimatedDays(Integer estimatedDays) {
        this.estimatedDays = estimatedDays;
    }

    public RepairServiceStatus getStatus() {
        return status;
    }

    public void setStatus(RepairServiceStatus status) {
        this.status = status;
    }
}
