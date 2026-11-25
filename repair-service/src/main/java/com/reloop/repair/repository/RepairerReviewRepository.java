package com.reloop.repair.repository;

import com.reloop.repair.entity.RepairerReview;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Ratings of repairer workshops. */
public interface RepairerReviewRepository extends JpaRepository<RepairerReview, Long> {

    List<RepairerReview> findByRepairerProfile_Id(Long repairerProfileId);

    /** Latest reviews for the public profile page. */
    List<RepairerReview> findTop5ByRepairerProfile_IdOrderByCreatedAtDescIdDesc(Long repairerProfileId);
}
