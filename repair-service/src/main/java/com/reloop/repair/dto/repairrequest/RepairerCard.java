package com.reloop.repair.dto.repairrequest;

import com.reloop.repair.dto.repairer.LatLng;

/** The assigned workshop, with where to bring or send the item. */
public record RepairerCard(
        Long profileId,
        String businessName,
        String phone,
        String addressLine,
        String city,
        String pincode,
        LatLng location) {
}
