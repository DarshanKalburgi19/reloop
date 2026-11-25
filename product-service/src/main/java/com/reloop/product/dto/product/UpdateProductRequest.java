package com.reloop.product.dto.product;

import com.reloop.product.enums.ProductCategory;
import com.reloop.product.enums.ProductCondition;
import com.reloop.product.enums.ProductStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Body of {@code PUT /api/products/{id}}: a full replacement of the editable fields.
 *
 * <ul>
 *   <li>Optional fields left out are cleared: {@code pickupLocation}, {@code storeId}, and
 *       {@code imageUrls} (which falls back to the category placeholder).</li>
 *   <li>{@code status} is optional. {@code ARCHIVED} deactivates the listing, {@code ACTIVE}
 *       reactivates it, and leaving it out keeps the current status.</li>
 *   <li>Sold or reserved listings can't be edited.</li>
 * </ul>
 */
public record UpdateProductRequest(
        @NotBlank @Size(min = 3, max = 200) String title,
        @NotBlank @Size(max = 4000) String description,
        @NotNull ProductCategory category,
        @NotNull @DecimalMin("1.00") @DecimalMax("10000000.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @NotNull ProductCondition condition,
        @NotBlank @Size(max = 120) String city,
        @Size(max = 255) String pickupLocation,
        @Positive Long storeId,
        @Size(max = ProductValidation.MAX_IMAGES) List<@NotBlank @Size(max = 1000)
                @Pattern(regexp = ProductValidation.IMAGE_URL, message = ProductValidation.IMAGE_URL_MESSAGE) String> imageUrls,
        ProductStatus status) {
}
