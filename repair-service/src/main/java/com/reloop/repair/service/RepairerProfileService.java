package com.reloop.repair.service;

import com.reloop.repair.dto.CreateRepairerProfileRequest;
import com.reloop.repair.dto.RepairerProfileResponse;
import com.reloop.repair.entity.RepairerProfile;
import com.reloop.repair.enums.RepairerProfileStatus;
import com.reloop.repair.enums.VerificationStatus;
import com.reloop.repair.exception.ProfileAlreadyExistsException;
import com.reloop.repair.exception.ProfileNotFoundException;
import com.reloop.repair.repository.RepairerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates and reads repairer workshop profiles. */
@Service
public class RepairerProfileService {

    private final RepairerProfileRepository profiles;

    public RepairerProfileService(RepairerProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional
    public RepairerProfileResponse create(CreateRepairerProfileRequest req) {
        if (!req.acceptsInPerson() && !req.acceptsPickupDrop() && !req.acceptsShipping()) {
            throw new IllegalArgumentException("At least one repair method must be accepted");
        }
        if (profiles.findByAccountExternalId(req.accountExternalId()).isPresent()) {
            throw new ProfileAlreadyExistsException(req.accountExternalId());
        }
        RepairerProfile p = new RepairerProfile();
        p.setAccountExternalId(req.accountExternalId());
        p.setBusinessName(req.businessName().trim());
        p.setSpecialties(req.specialties());
        p.setPhone(req.phone());
        p.setAddressLine(req.addressLine().trim());
        p.setCity(req.city().trim());
        p.setPincode(req.pincode());
        p.setLatitude(req.latitude());
        p.setLongitude(req.longitude());
        p.setServiceHours(req.serviceHours().trim());
        p.setAcceptsInPerson(req.acceptsInPerson());
        p.setAcceptsPickupDrop(req.acceptsPickupDrop());
        p.setAcceptsShipping(req.acceptsShipping());
        p.setVerificationStatus(VerificationStatus.UNVERIFIED); // never self-verified
        p.setStatus(RepairerProfileStatus.ACTIVE);
        return RepairerProfileResponse.from(profiles.saveAndFlush(p));
    }

    @Transactional(readOnly = true)
    public RepairerProfileResponse get(Long id) {
        return profiles.findById(id).map(RepairerProfileResponse::from)
                .orElseThrow(() -> new ProfileNotFoundException(id));
    }
}
