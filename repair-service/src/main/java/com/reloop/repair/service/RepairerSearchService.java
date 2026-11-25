package com.reloop.repair.service;

import com.reloop.repair.dto.repairer.LatLng;
import com.reloop.repair.dto.repairer.MapBounds;
import com.reloop.repair.dto.repairer.NearbyResponse;
import com.reloop.repair.dto.repairer.NearbySearchCriteria;
import com.reloop.repair.dto.repairer.PageResponse;
import com.reloop.repair.dto.repairer.RepairerSearchCriteria;
import com.reloop.repair.dto.repairer.RepairerSummaryResponse;
import com.reloop.repair.entity.RepairerProfile;
import com.reloop.repair.repository.RepairerProfileRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Customer-facing repairer search: by city/category/price/method ({@code GET /api/repairers}) and by distance ({@code /nearby}). */
@Service
public class RepairerSearchService {

    private final RepairerProfileRepository profiles;

    public RepairerSearchService(RepairerProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public PageResponse<RepairerSummaryResponse> search(RepairerSearchCriteria c) {
        Long minPaise = c.minPrice() == null ? null : Money.ceilPaise(c.minPrice());
        Long maxPaise = c.maxPrice() == null ? null : Money.floorPaise(c.maxPrice());
        Specification<RepairerProfile> spec = RepairerSpecifications.listed().and(RepairerSpecifications.filters(
                c.city(), c.category(), minPaise, maxPaise, c.method(), Boolean.TRUE.equals(c.availableOnly()), c.q()));
        PageRequest page = PageRequest.of(c.pageOrDefault(), c.sizeOrDefault(), sortFor(c.sortOrDefault()));
        ServiceFilter filter = new ServiceFilter(c.category(), minPaise, maxPaise);
        return PageResponse.of(profiles.findAll(spec, page), p -> RepairerMapper.toSummary(p, filter));
    }

    @Transactional(readOnly = true)
    public NearbyResponse nearby(NearbySearchCriteria c) {
        double radiusKm = c.radiusOrDefault();
        Long minPaise = c.minPrice() == null ? null : Money.ceilPaise(c.minPrice());
        Long maxPaise = c.maxPrice() == null ? null : Money.floorPaise(c.maxPrice());
        GeoUtils.BoundingBox box = GeoUtils.boundingBox(c.lat(), c.lng(), radiusKm);

        Specification<RepairerProfile> spec = RepairerSpecifications.listed()
                .and(RepairerSpecifications.filters(null, c.category(), minPaise, maxPaise, c.method(),
                        Boolean.TRUE.equals(c.availableOnly()), null))
                .and(RepairerSpecifications.withinBox(box));
        ServiceFilter filter = new ServiceFilter(c.category(), minPaise, maxPaise);

        List<RepairerSummaryResponse> results = profiles.findAll(spec).stream()
                .map(p -> new Candidate(p, GeoUtils.distanceKm(c.lat(), c.lng(), p.getLatitude(), p.getLongitude())))
                .filter(candidate -> candidate.distanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(Candidate::distanceKm)
                        .thenComparing(candidate -> candidate.profile().getId()))
                .limit(c.limitOrDefault())
                .map(candidate -> RepairerMapper.toSummary(candidate.profile(), filter)
                        .withDistance(BigDecimal.valueOf(candidate.distanceKm()).setScale(2, RoundingMode.HALF_UP).doubleValue()))
                .toList();

        MapBounds bounds = new MapBounds(new LatLng(box.south(), box.west()), new LatLng(box.north(), box.east()));
        return new NearbyResponse(new LatLng(c.lat(), c.lng()), radiusKm, bounds, results.size(), results);
    }

    static Sort sortFor(String sort) {
        return switch (sort) {
            case "price_asc" -> Sort.by(Sort.Order.asc("startingPricePaise"), Sort.Order.asc("id"));
            case "price_desc" -> Sort.by(Sort.Order.desc("startingPricePaise"), Sort.Order.asc("id"));
            case "name" -> Sort.by(Sort.Order.asc("businessName"), Sort.Order.asc("id"));
            case "newest" -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            default -> Sort.by(Sort.Order.desc("ratingAverage"), Sort.Order.desc("ratingCount"), Sort.Order.asc("id"));
        };
    }

    private record Candidate(RepairerProfile profile, double distanceKm) {
    }
}
