package com.reloop.repair.entity;

import com.reloop.repair.enums.AvailabilityStatus;
import com.reloop.repair.enums.RepairServiceCategory;
import com.reloop.repair.enums.RepairerProfileStatus;
import com.reloop.repair.enums.VerificationStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.hibernate.annotations.ColumnDefault;

/**
 * A repair professional's public profile: workshop location (for the Leaflet
 * map), service hours and accepted delivery modes.
 *
 * <p>{@code accountExternalId} is the product-service Account ID (login) -
 * a plain Long, NO FK across microservices. Seeded demo workshops that have no login use
 * negative placeholder IDs (real account IDs are always positive) and {@code demo = true}.</p>
 *
 * <p>A profile created at registration only has the basics. It appears in customer search
 * ({@code profileComplete = true}) once the repairer has added a description, categories,
 * at least one service with a price, and working hours via {@code POST/PUT /api/repairers}.</p>
 */
@Entity
@Table(
    name = "repairer_profiles",
    uniqueConstraints = @UniqueConstraint(name = "uk_profiles_account", columnNames = "account_external_id"),
    indexes = {
        @Index(name = "ix_profiles_status", columnList = "status"),
        @Index(name = "ix_profiles_city", columnList = "city"),
        @Index(name = "ix_profiles_business_name", columnList = "business_name"),
        @Index(name = "ix_profiles_listing", columnList = "status, profile_complete"),
        @Index(name = "ix_profiles_lat_lng", columnList = "latitude, longitude")
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

    /* ── Workshop profile module ───────────────────────────────────────── */

    /** Repair categories the workshop handles (filterable). */
    @ElementCollection
    @CollectionTable(name = "repairer_profile_categories",
            joinColumns = @JoinColumn(name = "repairer_profile_id"),
            foreignKey = @ForeignKey(name = "fk_profile_categories_profile"),
            indexes = @Index(name = "ix_profile_categories_category", columnList = "category"))
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private Set<RepairServiceCategory> categories = new LinkedHashSet<>();

    /** Structured opening hours; days without an entry are closed. */
    @ElementCollection
    @CollectionTable(name = "repairer_working_hours",
            joinColumns = @JoinColumn(name = "repairer_profile_id"),
            foreignKey = @ForeignKey(name = "fk_working_hours_profile"))
    private List<WorkingHours> workingHours = new ArrayList<>();

    /** All services ever offered; removed services are kept as INACTIVE (repair requests reference them). */
    @OneToMany(mappedBy = "repairerProfile", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @OrderBy("id ASC")
    private List<RepairService> services = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'AVAILABLE'")
    @Column(name = "availability", nullable = false, length = 20)
    private AvailabilityStatus availability = AvailabilityStatus.AVAILABLE;

    @Column(name = "availability_note", length = 200)
    private String availabilityNote;

    /** Lowest ACTIVE service price in paise (search price sort); null when there are no services. */
    @Column(name = "starting_price_paise")
    private Long startingPricePaise;

    /** Average star rating (0 when there are no ratings yet), kept in sync with repairer_reviews. */
    @ColumnDefault("0")
    @Column(name = "rating_average", nullable = false)
    private double ratingAverage;

    @ColumnDefault("0")
    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    /** True when the profile has everything customers need; only complete profiles are searchable. */
    @ColumnDefault("false")
    @Column(name = "profile_complete", nullable = false)
    private boolean profileComplete;

    /** Seeded demo data, not a real business. Shown to customers as {@code demo: true}. */
    @ColumnDefault("false")
    @Column(name = "is_demo", nullable = false)
    private boolean demo;

    public RepairerProfile() {
    }

    public Set<RepairServiceCategory> getCategories() {
        return categories;
    }

    public List<WorkingHours> getWorkingHours() {
        return workingHours;
    }

    public List<RepairService> getServices() {
        return services;
    }

    public AvailabilityStatus getAvailability() {
        return availability;
    }

    public void setAvailability(AvailabilityStatus availability) {
        this.availability = availability;
    }

    public String getAvailabilityNote() {
        return availabilityNote;
    }

    public void setAvailabilityNote(String availabilityNote) {
        this.availabilityNote = availabilityNote;
    }

    public Long getStartingPricePaise() {
        return startingPricePaise;
    }

    public void setStartingPricePaise(Long startingPricePaise) {
        this.startingPricePaise = startingPricePaise;
    }

    public double getRatingAverage() {
        return ratingAverage;
    }

    public void setRatingAverage(double ratingAverage) {
        this.ratingAverage = ratingAverage;
    }

    public int getRatingCount() {
        return ratingCount;
    }

    public void setRatingCount(int ratingCount) {
        this.ratingCount = ratingCount;
    }

    public boolean isProfileComplete() {
        return profileComplete;
    }

    public void setProfileComplete(boolean profileComplete) {
        this.profileComplete = profileComplete;
    }

    public boolean isDemo() {
        return demo;
    }

    public void setDemo(boolean demo) {
        this.demo = demo;
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
