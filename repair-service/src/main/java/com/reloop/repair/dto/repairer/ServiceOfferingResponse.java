package com.reloop.repair.dto.repairer;

import com.reloop.repair.enums.PriceType;
import com.reloop.repair.enums.RepairServiceCategory;
import java.math.BigDecimal;

/** A service as customers see it. {@code priceLabel} is display-ready: "₹199" or "From ₹1,499". */
public record ServiceOfferingResponse(
        Long id,
        String name,
        String description,
        RepairServiceCategory category,
        PriceType priceType,
        BigDecimal price,
        long pricePaise,
        String priceLabel,
        Integer estimatedDays) {
}
