package com.reloop.product.entity;

import com.reloop.product.enums.ProductHistoryEventType;
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
 * One append-only entry in a product passport's history: listed, edited, price changed,
 * deactivated, reactivated, sold, relisted. The time is {@code createdAt}.
 */
@Entity
@Table(name = "product_history_events", indexes = {
    @Index(name = "ix_history_passport_created", columnList = "passport_id, created_at"),
    @Index(name = "ix_history_product", columnList = "product_id")
})
public class ProductHistoryEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "passport_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_history_passport"))
    private ProductPassport passport;

    /** The listing the event happened on. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_history_product"))
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30, updatable = false)
    private ProductHistoryEventType eventType;

    /** Who caused the event (seller, or the buyer for SOLD). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_account_id", updatable = false,
            foreignKey = @ForeignKey(name = "fk_history_actor"))
    private Account actor;

    /** Listing price at the time of the event, in paise. */
    @Column(name = "price_paise", updatable = false)
    private Long pricePaise;

    @Column(name = "details", length = 500, updatable = false)
    private String details;

    public ProductHistoryEvent() {
    }

    public ProductHistoryEvent(ProductPassport passport, Product product, ProductHistoryEventType eventType,
                               Account actor, Long pricePaise, String details) {
        this.passport = passport;
        this.product = product;
        this.eventType = eventType;
        this.actor = actor;
        this.pricePaise = pricePaise;
        this.details = details;
    }

    public ProductPassport getPassport() {
        return passport;
    }

    public Product getProduct() {
        return product;
    }

    public ProductHistoryEventType getEventType() {
        return eventType;
    }

    public Account getActor() {
        return actor;
    }

    public Long getPricePaise() {
        return pricePaise;
    }

    public String getDetails() {
        return details;
    }
}
