package com.reloop.repair.service;

import com.reloop.repair.entity.RepairService;
import com.reloop.repair.entity.RepairerProfile;
import com.reloop.repair.enums.AvailabilityStatus;
import com.reloop.repair.enums.RepairMethod;
import com.reloop.repair.enums.RepairServiceCategory;
import com.reloop.repair.enums.RepairServiceStatus;
import com.reloop.repair.enums.RepairerProfileStatus;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

/** JPA Criteria building blocks for customer search ({@code GET /api/repairers}) and {@code /nearby}. */
public final class RepairerSpecifications {

    private static final char LIKE_ESCAPE = '\\';
    private static final int MAX_KEYWORDS = 5;

    private RepairerSpecifications() {
    }

    /** Only ACTIVE, complete profiles appear in search. */
    public static Specification<RepairerProfile> listed() {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), RepairerProfileStatus.ACTIVE),
                cb.isTrue(root.<Boolean>get("profileComplete")));
    }

    /**
     * Shared customer filters. {@code minPaise}/{@code maxPaise} match workshops that offer at least one
     * ACTIVE service in the price range (and in {@code category}, when given), so "laptop repair between
     * ₹500 and ₹2,000" means one laptop service in that range.
     */
    public static Specification<RepairerProfile> filters(String city, RepairServiceCategory category, Long minPaise,
                                                         Long maxPaise, RepairMethod method, boolean availableOnly,
                                                         String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (city != null && !city.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.<String>get("city")), city.trim().toLowerCase(Locale.ROOT)));
            }
            if (category != null) {
                predicates.add(cb.isMember(category, root.<Set<RepairServiceCategory>>get("categories")));
            }
            if (minPaise != null || maxPaise != null) {
                Subquery<Long> sq = query.subquery(Long.class);
                Root<RepairService> s = sq.from(RepairService.class);
                List<Predicate> sp = new ArrayList<>();
                sp.add(cb.equal(s.get("repairerProfile"), root));
                sp.add(cb.equal(s.get("status"), RepairServiceStatus.ACTIVE));
                if (category != null) {
                    sp.add(cb.equal(s.get("category"), category));
                }
                if (minPaise != null) {
                    sp.add(cb.greaterThanOrEqualTo(s.<Long>get("estimatedPricePaise"), minPaise));
                }
                if (maxPaise != null) {
                    sp.add(cb.lessThanOrEqualTo(s.<Long>get("estimatedPricePaise"), maxPaise));
                }
                sq.select(s.<Long>get("id")).where(sp.toArray(new Predicate[0]));
                predicates.add(cb.exists(sq));
            }
            if (method != null) {
                String attribute = switch (method) {
                    case IN_PERSON -> "acceptsInPerson";
                    case PICKUP_DROPOFF -> "acceptsPickupDrop";
                    case SEND_ITEM -> "acceptsShipping";
                };
                predicates.add(cb.isTrue(root.<Boolean>get(attribute)));
            }
            if (availableOnly) {
                predicates.add(cb.equal(root.get("availability"), AvailabilityStatus.AVAILABLE));
            }
            for (String term : keywords(q)) {
                String pattern = "%" + escapeLike(term) + "%";
                Expression<String> name = cb.lower(root.<String>get("businessName"));
                Expression<String> description = cb.lower(cb.coalesce(root.<String>get("description"), ""));
                Expression<String> expertise = cb.lower(cb.coalesce(root.<String>get("specialties"), ""));
                predicates.add(cb.or(cb.like(name, pattern, LIKE_ESCAPE), cb.like(description, pattern, LIKE_ESCAPE),
                        cb.like(expertise, pattern, LIKE_ESCAPE)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Cheap, index-friendly pre-filter for /nearby; the exact distance is checked afterwards. */
    public static Specification<RepairerProfile> withinBox(GeoUtils.BoundingBox box) {
        return (root, query, cb) -> cb.and(
                cb.isNotNull(root.get("latitude")),
                cb.isNotNull(root.get("longitude")),
                cb.between(root.<Double>get("latitude"), box.south(), box.north()),
                cb.between(root.<Double>get("longitude"), box.west(), box.east()));
    }

    static List<String> keywords(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return Arrays.stream(q.trim().toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(s -> !s.isEmpty()).distinct().limit(MAX_KEYWORDS).toList();
    }

    static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
