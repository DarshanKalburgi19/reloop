package com.reloop.repair.service;

import com.reloop.repair.dto.repairer.RepairerDetailResponse;
import com.reloop.repair.dto.repairer.RepairerProfileRequest;
import com.reloop.repair.dto.repairer.ServiceOfferingRequest;
import com.reloop.repair.dto.repairer.WorkingHoursRequest;
import com.reloop.repair.entity.RepairService;
import com.reloop.repair.entity.RepairerProfile;
import com.reloop.repair.entity.WorkingHours;
import com.reloop.repair.enums.RepairMethod;
import com.reloop.repair.enums.RepairServiceStatus;
import com.reloop.repair.enums.RepairerProfileStatus;
import com.reloop.repair.enums.VerificationStatus;
import com.reloop.repair.exception.ProfileNotFoundException;
import com.reloop.repair.exception.ProfileOwnershipException;
import com.reloop.repair.exception.ProfileStateException;
import com.reloop.repair.exception.RequestValidationException;
import com.reloop.repair.exception.WorkshopProfileExistsException;
import com.reloop.repair.repository.RepairerProfileRepository;
import com.reloop.repair.repository.RepairerReviewRepository;
import com.reloop.repair.security.AuthenticatedAccount;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repairer-facing workshop profile management: create, edit, view own profile; plus the public profile view.
 *
 * <ul>
 *   <li>One profile per repairer account (the token's account ID). A second create returns 409 with the existing id.</li>
 *   <li>Only the owner can edit (403). Incomplete or non-ACTIVE profiles look like 404 to everyone else.</li>
 *   <li>Edits never change verification status, profile status, ratings or the demo flag.</li>
 *   <li>Services removed in an edit become INACTIVE (repair requests may reference them).</li>
 * </ul>
 */
@Service
public class WorkshopProfileService {

    private final RepairerProfileRepository profiles;
    private final RepairerReviewRepository reviews;

    public WorkshopProfileService(RepairerProfileRepository profiles, RepairerReviewRepository reviews) {
        this.profiles = profiles;
        this.reviews = reviews;
    }

    @Transactional
    public RepairerDetailResponse create(AuthenticatedAccount caller, RepairerProfileRequest req) {
        requireRepairer(caller);
        profiles.findByAccountExternalId(caller.accountId()).ifPresent(existing -> {
            throw new WorkshopProfileExistsException(existing.getId());
        });
        RepairerProfile profile = createProfile(caller.accountId(), req, false);
        return detail(profile, caller.accountId());
    }

    /** Creates a complete profile for an account. Also used by the demo seeder (demo = true). */
    @Transactional
    public RepairerProfile createProfile(long accountExternalId, RepairerProfileRequest req, boolean demo) {
        RepairerProfile profile = new RepairerProfile();
        profile.setAccountExternalId(accountExternalId);
        profile.setDemo(demo);
        profile.setVerificationStatus(VerificationStatus.UNVERIFIED); // never self-verified
        profile.setStatus(RepairerProfileStatus.ACTIVE);
        apply(profile, req, null);
        return profiles.saveAndFlush(profile);
    }

    @Transactional
    public RepairerDetailResponse update(AuthenticatedAccount caller, Long profileId, RepairerProfileRequest req) {
        requireRepairer(caller);
        RepairerProfile profile = profiles.findById(profileId).orElseThrow(() -> new ProfileNotFoundException(profileId));
        if (!isOwner(profile, caller)) {
            if (!isPublic(profile)) {
                throw new ProfileNotFoundException(profileId); // don't reveal hidden profiles
            }
            throw new ProfileOwnershipException();
        }
        if (profile.getStatus() == RepairerProfileStatus.SUSPENDED) {
            throw new ProfileStateException("This workshop profile is suspended and cannot be edited. Contact ReLoop support.");
        }
        Set<Long> existingServiceIds = profile.getServices().stream().map(RepairService::getId).collect(Collectors.toSet());
        apply(profile, req, existingServiceIds);
        profiles.flush(); // managed entity: new services are persisted by cascade on flush
        return detail(profile, caller.accountId());
    }

    /** Public profile. Incomplete or non-ACTIVE profiles are visible to their owner only (404 for everyone else). */
    @Transactional(readOnly = true)
    public RepairerDetailResponse get(Long profileId, AuthenticatedAccount viewer) {
        RepairerProfile profile = profiles.findById(profileId).orElseThrow(() -> new ProfileNotFoundException(profileId));
        if (!isPublic(profile) && !isOwner(profile, viewer)) {
            throw new ProfileNotFoundException(profileId);
        }
        return detail(profile, viewer == null ? null : viewer.accountId());
    }

    @Transactional(readOnly = true)
    public RepairerDetailResponse mine(AuthenticatedAccount caller) {
        requireRepairer(caller);
        RepairerProfile profile = profiles.findByAccountExternalId(caller.accountId())
                .orElseThrow(ProfileNotFoundException::noOwnProfile);
        return detail(profile, caller.accountId());
    }

    /* ───────────────────────────── helpers ───────────────────────────── */

    private RepairerDetailResponse detail(RepairerProfile profile, Long viewerAccountId) {
        return RepairerMapper.toDetail(profile,
                reviews.findTop5ByRepairerProfile_IdOrderByCreatedAtDescIdDesc(profile.getId()), viewerAccountId);
    }

    private static void requireRepairer(AuthenticatedAccount caller) {
        if (caller == null || !caller.isRepairer()) {
            throw new AccessDeniedException("Only repairer accounts can manage workshop profiles");
        }
    }

    /** Same rule as search: ACTIVE and complete. */
    private static boolean isPublic(RepairerProfile profile) {
        return profile.getStatus() == RepairerProfileStatus.ACTIVE && profile.isProfileComplete();
    }

    private static boolean isOwner(RepairerProfile profile, AuthenticatedAccount caller) {
        return caller != null && Objects.equals(profile.getAccountExternalId(), caller.accountId());
    }

    /** Validates cross-field rules, then copies the request onto the profile (full replacement). */
    private static void apply(RepairerProfile p, RepairerProfileRequest req, Set<Long> existingServiceIds) {
        Map<String, String> errors = ProfileRequestRules.check(req, existingServiceIds);
        if (!errors.isEmpty()) {
            throw new RequestValidationException(errors);
        }
        p.setBusinessName(req.businessName().trim());
        p.setDescription(req.description().trim());

        p.getCategories().clear();
        req.categories().stream().sorted().forEach(p.getCategories()::add);
        p.setSpecialties(req.expertise() == null || req.expertise().isEmpty() ? null
                : String.join(", ", req.expertise().stream().map(String::trim).toList()));

        p.setPhone(blankToNull(req.phone()));
        p.setAddressLine(req.addressLine().trim());
        p.setCity(ProfileRequestRules.normaliseCity(req.city()));
        p.setPincode(req.pincode());
        p.setLatitude(GeoUtils.round6(req.latitude()));
        p.setLongitude(GeoUtils.round6(req.longitude()));

        p.getWorkingHours().clear();
        req.workingHours().stream()
                .sorted(Comparator.comparing(WorkingHoursRequest::day))
                .map(h -> new WorkingHours(h.day(), LocalTime.parse(h.opens()), LocalTime.parse(h.closes())))
                .forEach(p.getWorkingHours()::add);
        p.setServiceHours(WorkingHoursFormatter.summary(p.getWorkingHours()));

        p.setAcceptsInPerson(req.repairMethods().contains(RepairMethod.IN_PERSON));
        p.setAcceptsShipping(req.repairMethods().contains(RepairMethod.SEND_ITEM));
        p.setAcceptsPickupDrop(req.repairMethods().contains(RepairMethod.PICKUP_DROPOFF));
        p.setAvailability(req.availability());
        p.setAvailabilityNote(blankToNull(req.availabilityNote()));

        syncServices(p, req.services());
        p.setStartingPricePaise(RepairerMapper.activeServices(p).stream()
                .map(RepairService::getEstimatedPricePaise).min(Long::compare).orElse(null));
        p.setProfileComplete(RepairerMapper.missingForListing(p).isEmpty());
    }

    private static void syncServices(RepairerProfile p, List<ServiceOfferingRequest> requested) {
        Map<Long, RepairService> existing = new HashMap<>();
        for (RepairService s : p.getServices()) {
            existing.put(s.getId(), s);
        }
        Set<Long> kept = new HashSet<>();
        for (ServiceOfferingRequest r : requested) {
            RepairService s;
            if (r.id() != null) {
                s = existing.get(r.id());
                kept.add(r.id());
            } else {
                s = new RepairService();
                s.setRepairerProfile(p);
                p.getServices().add(s);
            }
            s.setName(r.name().trim());
            s.setDescription(blankToNull(r.description()));
            s.setCategory(r.category());
            s.setPriceType(r.priceType());
            s.setEstimatedPricePaise(Money.toPaise(r.price()));
            s.setEstimatedDays(r.estimatedDays());
            s.setStatus(RepairServiceStatus.ACTIVE);
        }
        for (Map.Entry<Long, RepairService> e : existing.entrySet()) {
            if (!kept.contains(e.getKey())) {
                e.getValue().setStatus(RepairServiceStatus.INACTIVE);
            }
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
