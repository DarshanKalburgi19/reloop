package com.reloop.product.dto.auth;

import com.reloop.product.enums.RepairMethod;
import java.util.List;
import java.util.Set;

/** Workshop details of a repairer (source of truth: repair-service repairer_profiles). */
public record WorkshopDetails(
        String workshopName,
        List<String> repairExpertise,
        String workshopAddress,
        String city,
        String pincode,
        Double latitude,
        Double longitude,
        String serviceHours,
        Set<RepairMethod> repairMethods,
        String verificationStatus) {
}
