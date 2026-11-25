package com.reloop.repair.service;

import com.reloop.repair.dto.repairer.RepairerProfileRequest;
import com.reloop.repair.dto.repairer.ServiceOfferingRequest;
import com.reloop.repair.dto.repairer.WorkingHoursRequest;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Cross-field rules for {@link RepairerProfileRequest} that Bean Validation can't express.
 * Runs after the annotation checks passed, so fields are present and well-formed.
 */
public final class ProfileRequestRules {

    private ProfileRequestRules() {
    }

    /**
     * @param existingServiceIds ids of this workshop's services (update), or null when creating
     * @return field path -> message; empty when valid
     */
    public static Map<String, String> check(RepairerProfileRequest req, Set<Long> existingServiceIds) {
        Map<String, String> errors = new LinkedHashMap<>();
        checkLocation(req.latitude(), req.longitude(), errors);

        Set<String> names = new HashSet<>();
        Set<Long> ids = new HashSet<>();
        List<ServiceOfferingRequest> services = req.services();
        for (int i = 0; i < services.size(); i++) {
            ServiceOfferingRequest s = services.get(i);
            String at = "services[" + i + "]";
            if (!req.categories().contains(s.category())) {
                errors.putIfAbsent(at + ".category", "must be one of the workshop's categories " + req.categories());
            }
            if (!names.add(s.name().trim().toLowerCase(Locale.ROOT))) {
                errors.putIfAbsent(at + ".name", "duplicate service name");
            }
            if (s.id() != null) {
                if (existingServiceIds == null) {
                    errors.putIfAbsent(at + ".id", "must be empty when creating a profile");
                } else if (!existingServiceIds.contains(s.id())) {
                    errors.putIfAbsent(at + ".id", "unknown service id for this workshop");
                } else if (!ids.add(s.id())) {
                    errors.putIfAbsent(at + ".id", "duplicate service id");
                }
            }
        }

        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        List<WorkingHoursRequest> hours = req.workingHours();
        for (int i = 0; i < hours.size(); i++) {
            WorkingHoursRequest h = hours.get(i);
            String at = "workingHours[" + i + "]";
            if (!days.add(h.day())) {
                errors.putIfAbsent(at + ".day", "duplicate day " + h.day());
            }
            if (!LocalTime.parse(h.opens()).isBefore(LocalTime.parse(h.closes()))) {
                errors.putIfAbsent(at + ".closes", "must be later than opens (overnight hours are not supported)");
            }
        }

        if (req.expertise() != null) {
            Set<String> tags = new HashSet<>();
            for (int i = 0; i < req.expertise().size(); i++) {
                if (!tags.add(req.expertise().get(i).trim().toLowerCase(Locale.ROOT))) {
                    errors.putIfAbsent("expertise[" + i + "]", "duplicate expertise");
                }
            }
        }
        return errors;
    }

    /** Workshop coordinates must be in India; swapped latitude/longitude (a classic GeoJSON mix-up) is called out. */
    static void checkLocation(double lat, double lng, Map<String, String> errors) {
        if (GeoUtils.inIndia(lat, lng)) {
            return;
        }
        if (GeoUtils.inIndia(lng, lat)) {
            errors.put("latitude", "latitude and longitude look swapped: in Gujarat latitude is about 20-24.7 "
                    + "and longitude about 68-74.5");
        } else {
            errors.put("latitude", "workshop location must be in India (latitude " + GeoUtils.INDIA_MIN_LAT + " to "
                    + GeoUtils.INDIA_MAX_LAT + ", longitude " + GeoUtils.INDIA_MIN_LNG + " to " + GeoUtils.INDIA_MAX_LNG + ")");
        }
    }

    /** "  ahmedabad " -> "Ahmedabad", "VALLABH  VIDYANAGAR" -> "Vallabh Vidyanagar"; mixed case is kept as typed. */
    public static String normaliseCity(String raw) {
        String city = raw.trim().replaceAll("\\s+", " ");
        if (!city.equals(city.toLowerCase(Locale.ROOT)) && !city.equals(city.toUpperCase(Locale.ROOT))) {
            return city;
        }
        StringBuilder sb = new StringBuilder(city.length());
        boolean startOfWord = true;
        for (char c : city.toLowerCase(Locale.ROOT).toCharArray()) {
            sb.append(startOfWord ? Character.toUpperCase(c) : c);
            startOfWord = c == ' ' || c == '-';
        }
        return sb.toString();
    }
}
