package com.reloop.repair.service;

import com.reloop.repair.dto.repairer.LatLng;
import com.reloop.repair.dto.repairer.RatingSummary;
import com.reloop.repair.dto.repairer.RepairerDetailResponse;
import com.reloop.repair.dto.repairer.RepairerSummaryResponse;
import com.reloop.repair.dto.repairer.ReviewResponse;
import com.reloop.repair.dto.repairer.ServiceOfferingResponse;
import com.reloop.repair.dto.repairer.WorkingHoursResponse;
import com.reloop.repair.entity.RepairService;
import com.reloop.repair.entity.RepairerProfile;
import com.reloop.repair.entity.RepairerReview;
import com.reloop.repair.entity.WorkingHours;
import com.reloop.repair.enums.PriceType;
import com.reloop.repair.enums.RepairMethod;
import com.reloop.repair.enums.RepairServiceCategory;
import com.reloop.repair.enums.RepairServiceStatus;
import com.reloop.repair.enums.RepairerProfileStatus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Entity -> API response mapping for workshop profiles. Call inside a transaction (lazy collections). */
public final class RepairerMapper {

    public static final String DEMO_NOTICE = "Demo profile - sample data for testing, not a real business";

    private RepairerMapper() {
    }

    public static RepairerSummaryResponse toSummary(RepairerProfile p, ServiceFilter filter) {
        Long cheapest = activeServices(p).stream().filter(filter::matches)
                .map(RepairService::getEstimatedPricePaise).min(Long::compare)
                .orElse(p.getStartingPricePaise());
        return new RepairerSummaryResponse(p.getId(), p.getBusinessName(), p.getCity(), p.getAddressLine(),
                p.getLatitude(), p.getLongitude(), location(p), categories(p), repairMethods(p),
                cheapest == null ? null : Money.toRupees(cheapest),
                cheapest == null ? null : "From " + Money.format(cheapest),
                rating(p), p.getAvailability(), p.getVerificationStatus(), p.isDemo(),
                p.isDemo() ? DEMO_NOTICE : null, null);
    }

    public static RepairerDetailResponse toDetail(RepairerProfile p, List<RepairerReview> reviews, Long viewerAccountId) {
        Long cheapest = p.getStartingPricePaise();
        List<String> missing = missingForListing(p);
        return new RepairerDetailResponse(
                p.getId(), p.getBusinessName(), p.getDescription(), categories(p), expertise(p.getSpecialties()),
                activeServices(p).stream().map(RepairerMapper::toService).toList(),
                cheapest == null ? null : Money.toRupees(cheapest),
                cheapest == null ? null : "From " + Money.format(cheapest),
                p.getPhone(), p.getAddressLine(), p.getCity(), p.getPincode(),
                p.getLatitude(), p.getLongitude(), location(p), mapUrl(p),
                p.getWorkingHours().stream()
                        .sorted(Comparator.comparing(WorkingHours::getDayOfWeek))
                        .map(h -> new WorkingHoursResponse(h.getDayOfWeek(), WorkingHoursFormatter.format(h.getOpensAt()),
                                WorkingHoursFormatter.format(h.getClosesAt())))
                        .toList(),
                p.getServiceHours(), repairMethods(p), p.getAvailability(), p.getAvailabilityNote(), rating(p),
                reviews.stream().map(r -> new ReviewResponse(r.getRating(), r.getComment(), r.getReviewerName(),
                        r.getCreatedAt(), r.isDemo())).toList(),
                p.getVerificationStatus(), p.getStatus(),
                p.getStatus() == RepairerProfileStatus.ACTIVE && p.isProfileComplete(), missing,
                viewerAccountId != null && Objects.equals(viewerAccountId, p.getAccountExternalId()),
                p.isDemo(), p.isDemo() ? DEMO_NOTICE : null, p.getCreatedAt(), p.getUpdatedAt());
    }

    public static ServiceOfferingResponse toService(RepairService s) {
        long paise = s.getEstimatedPricePaise();
        return new ServiceOfferingResponse(s.getId(), s.getName(), s.getDescription(), s.getCategory(), s.getPriceType(),
                Money.toRupees(paise), paise, priceLabel(s.getPriceType(), paise), s.getEstimatedDays());
    }

    public static String priceLabel(PriceType type, long paise) {
        return type == PriceType.STARTING_FROM ? "From " + Money.format(paise) : Money.format(paise);
    }

    /** ACTIVE services that have a price, ordered by category, then price. */
    public static List<RepairService> activeServices(RepairerProfile p) {
        return p.getServices().stream()
                .filter(s -> s.getStatus() == RepairServiceStatus.ACTIVE && s.getEstimatedPricePaise() != null)
                .sorted(Comparator.comparing(RepairService::getCategory)
                        .thenComparing(RepairService::getEstimatedPricePaise)
                        .thenComparing(RepairService::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** What a profile still needs before it is shown in customer search (empty = complete). */
    public static List<String> missingForListing(RepairerProfile p) {
        List<String> missing = new ArrayList<>();
        if (p.getDescription() == null || p.getDescription().isBlank()) {
            missing.add("description");
        }
        if (p.getCategories().isEmpty()) {
            missing.add("categories");
        }
        if (activeServices(p).isEmpty()) {
            missing.add("services");
        }
        if (p.getWorkingHours().isEmpty()) {
            missing.add("workingHours");
        }
        if (p.getPincode() == null || p.getPincode().isBlank()) {
            missing.add("pincode");
        }
        if (p.getLatitude() == null || p.getLongitude() == null) {
            missing.add("location");
        }
        if (repairMethods(p).isEmpty()) {
            missing.add("repairMethods");
        }
        return missing;
    }

    public static List<RepairServiceCategory> categories(RepairerProfile p) {
        return p.getCategories().stream().sorted().toList();
    }

    public static List<RepairMethod> repairMethods(RepairerProfile p) {
        List<RepairMethod> methods = new ArrayList<>();
        if (p.isAcceptsInPerson()) {
            methods.add(RepairMethod.IN_PERSON);
        }
        if (p.isAcceptsShipping()) {
            methods.add(RepairMethod.SEND_ITEM);
        }
        if (p.isAcceptsPickupDrop()) {
            methods.add(RepairMethod.PICKUP_DROPOFF);
        }
        return methods;
    }

    /** Stored comma-separated (registration used the same format). */
    public static List<String> expertise(String specialties) {
        if (specialties == null || specialties.isBlank()) {
            return List.of();
        }
        return Arrays.stream(specialties.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public static RatingSummary rating(RepairerProfile p) {
        if (p.getRatingCount() == 0) {
            return new RatingSummary(null, 0);
        }
        return new RatingSummary(GeoUtils.round1(p.getRatingAverage()), p.getRatingCount());
    }

    static LatLng location(RepairerProfile p) {
        return p.getLatitude() == null || p.getLongitude() == null ? null : new LatLng(p.getLatitude(), p.getLongitude());
    }

    static String mapUrl(RepairerProfile p) {
        if (p.getLatitude() == null || p.getLongitude() == null) {
            return null;
        }
        return "https://www.openstreetmap.org/?mlat=" + p.getLatitude() + "&mlon=" + p.getLongitude()
                + "#map=17/" + p.getLatitude() + "/" + p.getLongitude();
    }
}
