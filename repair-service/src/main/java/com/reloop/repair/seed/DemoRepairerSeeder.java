package com.reloop.repair.seed;

import com.reloop.repair.entity.RepairerProfile;
import com.reloop.repair.entity.RepairerReview;
import com.reloop.repair.repository.RepairerProfileRepository;
import com.reloop.repair.repository.RepairerReviewRepository;
import com.reloop.repair.service.RatingService;
import com.reloop.repair.service.WorkshopProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seeds the {@link DemoRepairers} workshops (all {@code demo = true}) and their demo ratings.
 * Enabled by {@code reloop.seed.enabled=true} (default in application.properties, off in tests).
 * Idempotent: skipped when the demo workshops already exist. Each workshop goes through the same
 * validation as {@code POST /api/repairers}.
 */
@Component
@ConditionalOnProperty(name = "reloop.seed.enabled", havingValue = "true")
public class DemoRepairerSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoRepairerSeeder.class);

    private final RepairerProfileRepository profiles;
    private final RepairerReviewRepository reviews;
    private final WorkshopProfileService profileService;
    private final RatingService ratingService;
    private final TransactionTemplate tx;

    public DemoRepairerSeeder(RepairerProfileRepository profiles, RepairerReviewRepository reviews,
                              WorkshopProfileService profileService, RatingService ratingService,
                              TransactionTemplate tx) {
        this.profiles = profiles;
        this.reviews = reviews;
        this.profileService = profileService;
        this.ratingService = ratingService;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (profiles.existsByDemoTrueAndAccountExternalIdLessThan(0L)) {
            log.info("Demo repairer workshops already present");
            return;
        }
        for (DemoRepairers.DemoWorkshop workshop : DemoRepairers.WORKSHOPS) {
            tx.executeWithoutResult(status -> seed(workshop));
        }
        log.warn("Seeded {} DEMO repairer workshops in Gujarat (demo=true, not real businesses)",
                DemoRepairers.WORKSHOPS.size());
    }

    private void seed(DemoRepairers.DemoWorkshop workshop) {
        RepairerProfile profile = profileService.createProfile(workshop.accountExternalId(), workshop.profile(), true);
        profile.setVerificationStatus(workshop.verification());
        for (DemoRepairers.DemoReview r : workshop.reviews()) {
            reviews.save(new RepairerReview(profile, r.rating(), r.comment(), r.reviewerName(), null, true));
        }
        reviews.flush();
        ratingService.recalculate(profile);
    }
}
