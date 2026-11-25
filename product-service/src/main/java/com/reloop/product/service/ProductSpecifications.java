package com.reloop.product.service;

import com.reloop.product.dto.product.ProductSearchCriteria;
import com.reloop.product.entity.Product;
import com.reloop.product.enums.ProductStatus;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Turns {@link ProductSearchCriteria} into one JPA Criteria query (filters are combined with AND).
 * The values are bound as parameters, never concatenated into SQL.
 */
public final class ProductSpecifications {

    private static final char LIKE_ESCAPE = '\\';
    private static final int MAX_KEYWORDS = 5;

    private ProductSpecifications() {
    }

    /** Browse/search: only ACTIVE listings, plus whatever filters are present. */
    public static Specification<Product> availableMatching(ProductSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), ProductStatus.ACTIVE));

            for (String term : keywords(c.q())) {
                String pattern = "%" + escapeLike(term) + "%";
                Expression<String> title = cb.lower(root.<String>get("title"));
                Expression<String> description = cb.lower(root.<String>get("description"));
                predicates.add(cb.or(
                        cb.like(title, pattern, LIKE_ESCAPE),
                        cb.like(description, pattern, LIKE_ESCAPE)));
            }
            if (c.category() != null) {
                predicates.add(cb.equal(root.get("category"), c.category()));
            }
            if (c.condition() != null) {
                predicates.add(cb.equal(root.get("condition"), c.condition()));
            }
            if (c.city() != null && !c.city().isBlank()) {
                predicates.add(cb.equal(cb.lower(root.<String>get("locationCity")), c.city().trim().toLowerCase(Locale.ROOT)));
            }
            if (c.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Long>get("pricePaise"), ceilPaise(c.minPrice())));
            }
            if (c.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Long>get("pricePaise"), floorPaise(c.maxPrice())));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** "  Sony   headphones " -> ["sony", "headphones"] (max 5 words). */
    static List<String> keywords(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return Arrays.stream(q.trim().toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(s -> !s.isEmpty())
                .distinct()
                .limit(MAX_KEYWORDS)
                .toList();
    }

    /** "100%_off" must match literally, not as LIKE wildcards. */
    static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static long ceilPaise(BigDecimal rupees) {
        return rupees.movePointRight(2).setScale(0, RoundingMode.CEILING).longValue();
    }

    private static long floorPaise(BigDecimal rupees) {
        return rupees.movePointRight(2).setScale(0, RoundingMode.FLOOR).longValue();
    }
}
