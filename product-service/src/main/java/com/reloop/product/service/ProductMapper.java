package com.reloop.product.service;

import com.reloop.product.dto.product.ProductImageResponse;
import com.reloop.product.dto.product.ProductResponse;
import com.reloop.product.dto.product.ProductSummaryResponse;
import com.reloop.product.dto.product.StoreSummary;
import com.reloop.product.entity.Product;
import com.reloop.product.entity.ProductImage;
import com.reloop.product.entity.Store;
import com.reloop.product.enums.ProductStatus;
import com.reloop.product.enums.ProductImageStatus;
import java.util.List;

/**
 * Entity to DTO mapping. Entities are never serialised directly, so lazy proxies, password
 * hashes or internal columns can't leak. Call it inside a transaction.
 */
final class ProductMapper {

    private ProductMapper() {
    }

    static ProductResponse toResponse(Product p) {
        List<ProductImageResponse> images = visibleImages(p).stream()
                .map(i -> new ProductImageResponse(i.getUrl(), i.getAltText(), i.isPrimary(),
                        PlaceholderImages.isPlaceholder(i.getUrl())))
                .toList();
        return new ProductResponse(
                p.getId(),
                p.getSeller().getId(),
                p.getSeller().getDisplayName(),
                p.getTitle(),
                p.getDescription(),
                p.getCategory(),
                Money.toRupees(p.getPricePaise()),
                p.getPricePaise(),
                Money.CURRENCY,
                p.getCondition(),
                p.getLocationCity(),
                toStore(p.getStore()),
                p.getPickupLocation(),
                images,
                p.getStatus(),
                p.getStatus() == ProductStatus.ACTIVE,
                p.getPassport().getPassportCode(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                p.getSoldAt());
    }

    static ProductSummaryResponse toSummary(Product p) {
        return new ProductSummaryResponse(
                p.getId(),
                p.getSeller().getId(),
                p.getSeller().getDisplayName(),
                p.getTitle(),
                p.getCategory(),
                Money.toRupees(p.getPricePaise()),
                p.getPricePaise(),
                Money.CURRENCY,
                p.getCondition(),
                p.getLocationCity(),
                p.getStore() != null ? p.getStore().getName() : null,
                p.getPickupLocation(),
                thumbnail(p),
                p.getStatus(),
                p.getPassport().getPassportCode(),
                p.getCreatedAt());
    }

    private static StoreSummary toStore(Store s) {
        if (s == null) {
            return null;
        }
        return new StoreSummary(s.getId(), s.getName(), s.getAddressLine(),
                s.getCity() != null ? s.getCity().getName() : null);
    }

    private static List<ProductImage> visibleImages(Product p) {
        return p.getImages().stream().filter(i -> i.getStatus() == ProductImageStatus.ACTIVE).toList();
    }

    private static String thumbnail(Product p) {
        List<ProductImage> images = visibleImages(p);
        return images.stream().filter(ProductImage::isPrimary).findFirst()
                .or(() -> images.stream().findFirst())
                .map(ProductImage::getUrl)
                .orElse(PlaceholderImages.forCategory(p.getCategory()));
    }
}
