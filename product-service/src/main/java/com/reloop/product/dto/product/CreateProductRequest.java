package com.reloop.product.dto.product;

import com.reloop.product.enums.ProductCategory;
import com.reloop.product.enums.ProductCondition;
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
 * Body of {@code POST /api/products}.
 *
 * <ul>
 *   <li>{@code price} is in rupees with at most 2 decimals (stored as integer paise).</li>
 *   <li>{@code storeId} (optional) must be an ACTIVE store owned by the seller.</li>
 *   <li>{@code imageUrls} (optional, max 8): when empty, a category placeholder is used.</li>
 *   <li>{@code passportCode} (optional): set it to relist an item you bought on ReLoop. The new
 *       listing then continues that item's passport history instead of starting a new one.</li>
 * </ul>
 * The seller is always the authenticated customer; it can't be set in the body.
 */
public record CreateProductRequest(
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
        @Size(max = 24) String passportCode) {
}
