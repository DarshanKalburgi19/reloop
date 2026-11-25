package com.reloop.repair.service;

import com.reloop.repair.entity.RepairService;
import com.reloop.repair.enums.RepairServiceCategory;

/** The service-level part of a customer search; decides which services the shown "starting price" is taken from. */
public record ServiceFilter(RepairServiceCategory category, Long minPaise, Long maxPaise) {

    public static final ServiceFilter NONE = new ServiceFilter(null, null, null);

    public boolean matches(RepairService s) {
        Long price = s.getEstimatedPricePaise();
        return (category == null || s.getCategory() == category)
                && (minPaise == null || (price != null && price >= minPaise))
                && (maxPaise == null || (price != null && price <= maxPaise));
    }
}
