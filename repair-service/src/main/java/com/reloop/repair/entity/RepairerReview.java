package com.reloop.repair.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;

/**
 * A customer's rating (1-5 stars) of a repairer workshop.
 *
 * <p>Ratings are summarised on {@link RepairerProfile} ({@code ratingAverage}, {@code ratingCount})
 * for fast search and sorting. Customers will submit reviews after a completed repair request
 * (repair requests phase); for now only the demo seeder creates reviews, all with {@code demo = true}.</p>
 */
@Entity
@Table(name = "repairer_reviews", indexes = {
    @Index(name = "ix_reviews_profile_created", columnList = "repairer_profile_id, created_at")
})
@Check(name = "ck_reviews_rating", constraints = "rating BETWEEN 1 AND 5")
public class RepairerReview extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repairer_profile_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_reviews_profile"))
    private RepairerProfile repairerProfile;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "comment", length = 1000)
    private String comment;

    /** Display name only, e.g. "Priya S." (never an email). */
    @Column(name = "reviewer_name", nullable = false, length = 80)
    private String reviewerName;

    /** product-service Account ID of the reviewer (no cross-service FK); null for demo reviews. */
    @Column(name = "customer_account_external_id")
    private Long customerAccountExternalId;

    @Column(name = "is_demo", nullable = false)
    private boolean demo;

    protected RepairerReview() {
    }

    public RepairerReview(RepairerProfile repairerProfile, int rating, String comment, String reviewerName,
                          Long customerAccountExternalId, boolean demo) {
        this.repairerProfile = repairerProfile;
        this.rating = rating;
        this.comment = comment;
        this.reviewerName = reviewerName;
        this.customerAccountExternalId = customerAccountExternalId;
        this.demo = demo;
    }

    public RepairerProfile getRepairerProfile() {
        return repairerProfile;
    }

    public int getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public String getReviewerName() {
        return reviewerName;
    }

    public Long getCustomerAccountExternalId() {
        return customerAccountExternalId;
    }

    public boolean isDemo() {
        return demo;
    }
}
