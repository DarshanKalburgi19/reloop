package com.reloop.repair.dto;

import com.reloop.repair.entity.RepairerProfile;
import com.reloop.repair.enums.RepairerProfileStatus;
import com.reloop.repair.enums.VerificationStatus;

/** Public view of a repairer workshop profile. */
public record RepairerProfileResponse(
        Long id,
        Long accountExternalId,
        String businessName,
        String specialties,
        String phone,
        String addressLine,
        String city,
        String pincode,
        Double latitude,
        Double longitude,
        String serviceHours,
        boolean acceptsInPerson,
        boolean acceptsPickupDrop,
        boolean acceptsShipping,
        VerificationStatus verificationStatus,
        RepairerProfileStatus status) {

    public static RepairerProfileResponse from(RepairerProfile p) {
        return new RepairerProfileResponse(p.getId(), p.getAccountExternalId(), p.getBusinessName(),
                p.getSpecialties(), p.getPhone(), p.getAddressLine(), p.getCity(), p.getPincode(),
                p.getLatitude(), p.getLongitude(), p.getServiceHours(), p.isAcceptsInPerson(),
                p.isAcceptsPickupDrop(), p.isAcceptsShipping(), p.getVerificationStatus(), p.getStatus());
    }
}
