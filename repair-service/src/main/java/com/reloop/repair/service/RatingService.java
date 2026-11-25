package com.reloop.repair.service;

import com.reloop.repair.entity.RepairerProfile;
import com.reloop.repair.entity.RepairerReview;
import com.reloop.repair.repository.RepairerReviewRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps the rating summary on {@link RepairerProfile} in sync with its reviews. */
@Service
public class RatingService {

    private final RepairerReviewRepository reviews;

    public RatingService(RepairerReviewRepository reviews) {
        this.reviews = reviews;
    }

    @Transactional
    public void recalculate(RepairerProfile profile) {
        List<RepairerReview> all = reviews.findByRepairerProfile_Id(profile.getId());
        profile.setRatingCount(all.size());
        double average = all.stream().mapToInt(RepairerReview::getRating).average().orElse(0);
        profile.setRatingAverage(Math.round(average * 100d) / 100d);
    }
}
