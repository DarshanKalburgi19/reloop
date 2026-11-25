package com.reloop.product.repository;

import com.reloop.product.entity.ProductImage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link ProductImage}.
 * Query methods are derived from their names - no SQL/JPQL needed.
 */
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProduct_IdOrderBySortOrder(Long productId);

}
