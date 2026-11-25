package com.reloop.repair.dto.repairrequest;

import com.reloop.repair.enums.RepairMethod;
import com.reloop.repair.enums.RepairServiceCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body of {@code POST /api/repair-requests} (CUSTOMER).
 *
 * <ul>
 *   <li>{@code repairerProfileId}: a workshop from {@code GET /api/repairers} (must be listed and not UNAVAILABLE).</li>
 *   <li>{@code category}: one of the workshop's categories.</li>
 *   <li>{@code repairServiceId}: optional, one of the workshop's active services in that category.</li>
 *   <li>{@code deliveryMethod}: IN_PERSON (bring it), SEND_ITEM (courier it) or PICKUP_DROPOFF; the workshop must offer it.</li>
 *   <li>{@code imageUrls}: up to 5 demo images ({@code /demo-images/repairs/...}) or https URLs.</li>
 * </ul>
 */
public record CreateRepairRequest(
        @NotNull @Positive Long repairerProfileId,
        @Positive Long repairServiceId,
        @NotNull RepairServiceCategory category,
        @NotBlank @Size(min = 3, max = 200) String itemDescription,
        @NotBlank @Size(min = 20, max = 2000) String problemDescription,
        @Size(max = RepairRequestValidation.MAX_IMAGES) List<@NotBlank @Size(max = 500)
                @Pattern(regexp = RepairRequestValidation.IMAGE_URL, message = RepairRequestValidation.IMAGE_URL_MESSAGE) String> imageUrls,
        @NotNull RepairMethod deliveryMethod,
        @Size(max = RepairRequestValidation.MAX_NOTE) String customerNotes) {
}
