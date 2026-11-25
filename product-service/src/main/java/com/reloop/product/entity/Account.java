package com.reloop.product.entity;

import com.reloop.product.enums.AccountRole;
import com.reloop.product.enums.AccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Shared login record for BOTH account types (internal shared login
 * infrastructure, per the master instruction).
 *
 * <p>The {@code role} column is fixed at registration: CUSTOMER accounts can
 * never be converted to REPAIRER accounts. Profile data lives in the separate
 * {@link CustomerAccount} / {@link RepairerAccountIdentity} tables.</p>
 */
@Entity
@Table(
    name = "accounts",
    uniqueConstraints = @UniqueConstraint(name = "uk_accounts_email", columnNames = "email"),
    indexes = @Index(name = "ix_accounts_role_status", columnList = "role, status")
)
public class Account extends BaseEntity {

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private AccountRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Column(name = "phone", length = 30)
    private String phone;

    public Account() {
    }

    public Account(String email, String passwordHash, AccountRole role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public AccountRole getRole() {
        return role;
    }

    public void setRole(AccountRole role) {
        this.role = role;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
