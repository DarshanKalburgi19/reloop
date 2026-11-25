package com.reloop.repair.entity;

import com.reloop.repair.enums.RepairerProfileStatus;
import com.reloop.repair.enums.VerificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A repair professional's public profile: workshop location (for the Leaflet
 * map), service hours and accepted delivery modes.
 *
 * <p>{@code accountExternalId} is the product-service Account ID (login) -
 * a plain Long, NO FK across microservices.</p>
 */
@Entity
@Table(
    name = "repairer_profiles",
    uniqueConstraints = @UniqueConstraint(name = "uk_profiles_account", columnNames = "account_external_id"),
    indexes = {
        @Index(name = "ix_profiles_status", columnList = "status"),
        @Index(name = "ix_profiles_city", columnList = "city"),
        @Index(name = "ix_profiles_business_name", columnList = "business_name")
    }
)
public class RepairerProfile extends BaseEntity {

    /** External product-service Account ID (no cross-service FK). */
    @Column(name = "account_external_id", nullable = false)
    private Long accountExternalId;

    @Column(name = "business_name", nullable = false, length = 150)
    private String businessName;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "specialties", length = 500)
    private String specialties;

    @Column(name = "phone", length = 30)
    private String phone;

    /* ── Workshop location (maps: Leaflet + OpenStreetMap) ─────────────── */

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(name = "city", length = 120)
    private String city;

    @Column(name = "pincode", length = 12)
    private String pincode;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    /* ── Availability & delivery options ───────────────────────────────── */

    @Column(name = "service_hours", length = 300)
    private String serviceHours;

    @Column(name = "accepts_in_person", nullable = false)
    private boolean acceptsInPerson = true;

    @Column(name = "accepts_pickup_drop", nullable = false)
    private boolean acceptsPickupDrop;

    @Column(name = "accepts_shipping", nullable = false)
    private boolean acceptsShipping;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RepairerProfileStatus status = RepairerProfileStatus.ACTIVE;

    public RepairerProfile() {
    }

    public Long getAccountExternalId() {
        return accountExternalId;
    }

    public void setAccountExternalId(Long accountExternalId) {
        this.accountExternalId = accountExternalId;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSpecialties() {
        return specialties;
    }

    public void setSpecialties(String specialties) {
        this.specialties = specialties;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public void setAddressLine(String addressLine) {
        this.addressLine = addressLine;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getPincode() {
        return pincode;
    }

    public void setPincode(String pincode) {
        this.pincode = pincode;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getServiceHours() {
        return serviceHours;
    }

    public void setServiceHours(String serviceHours) {
        this.serviceHours = serviceHours;
    }

    public boolean isAcceptsInPerson() {
        return acceptsInPerson;
    }

    public void setAcceptsInPerson(boolean acceptsInPerson) {
        this.acceptsInPerson = acceptsInPerson;
    }

    public boolean isAcceptsPickupDrop() {
        return acceptsPickupDrop;
    }

    public void setAcceptsPickupDrop(boolean acceptsPickupDrop) {
        this.acceptsPickupDrop = acceptsPickupDrop;
    }

    public boolean isAcceptsShipping() {
        return acceptsShipping;
    }

    public void setAcceptsShipping(boolean acceptsShipping) {
        this.acceptsShipping = acceptsShipping;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public RepairerProfileStatus getStatus() {
        return status;
    }

    public void setStatus(RepairerProfileStatus status) {
        this.status = status;
    }
}
