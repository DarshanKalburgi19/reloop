package com.reloop.product.entity;

import com.reloop.product.enums.VerificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * Marks that a shared {@link Account} (role = REPAIRER) also has a repairer
 * profile in the Repair & Tracking Service.
 *
 * <p>{@code repairerProfileId} is an <b>external service ID</b> - a plain Long
 * pointing at repair-service, deliberately NOT a JPA relationship, because the
 * two services have separate databases (microservice rule).</p>
 */
@Entity
@Table(
    name = "repairer_account_identities",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_rai_account", columnNames = "account_id"),
        @UniqueConstraint(name = "uk_rai_profile", columnNames = "repairer_profile_id")
    },
    indexes = @Index(name = "ix_rai_verification", columnList = "verification_status")
)
public class RepairerAccountIdentity extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "account_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_rai_account")
    )
    private Account account;

    /** External ID of repair-service RepairerProfile (no FK across services). */
    @Column(name = "repairer_profile_id", nullable = false)
    private Long repairerProfileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    public RepairerAccountIdentity() {
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public Long getRepairerProfileId() {
        return repairerProfileId;
    }

    public void setRepairerProfileId(Long repairerProfileId) {
        this.repairerProfileId = repairerProfileId;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }
}
