package com.reloop.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * The digital passport of one <b>physical item</b>.
 *
 * <p>Created when an item is first listed. It keeps its unique, public
 * {@code passportCode} (e.g. {@code RLP-7K3Q-X9M2-B4TD}) for life, even when the item is
 * bought and listed again by its new owner. Every listing ({@link Product}) and every
 * {@link ProductHistoryEvent} points to it, so the full history stays together.</p>
 */
@Entity
@Table(
    name = "product_passports",
    uniqueConstraints = @UniqueConstraint(name = "uk_passports_code", columnNames = "passport_code"),
    indexes = @Index(name = "ix_passports_owner", columnList = "current_owner_account_id")
)
public class ProductPassport extends BaseEntity {

    @Column(name = "passport_code", nullable = false, length = 24, updatable = false)
    private String passportCode;

    /** Who owns the item now: the first seller, then each buyer in turn. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "current_owner_account_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_passports_owner"))
    private Account currentOwner;

    public ProductPassport() {
    }

    public ProductPassport(String passportCode, Account currentOwner) {
        this.passportCode = passportCode;
        this.currentOwner = currentOwner;
    }

    public String getPassportCode() {
        return passportCode;
    }

    public void setPassportCode(String passportCode) {
        this.passportCode = passportCode;
    }

    public Account getCurrentOwner() {
        return currentOwner;
    }

    public void setCurrentOwner(Account currentOwner) {
        this.currentOwner = currentOwner;
    }
}
