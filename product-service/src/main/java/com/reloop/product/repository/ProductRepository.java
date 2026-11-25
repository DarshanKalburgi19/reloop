package com.reloop.product.repository;

import com.reloop.product.entity.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.reloop.product.enums.ProductStatus;

/**
 * Spring Data JPA repository for {@link Product}.
 * Query methods are derived from their names - no SQL/JPQL needed.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByStatus(ProductStatus status);
    List<Product> findBySeller_Id(Long sellerId);
    List<Product> findByStatusOrderByCreatedAtDesc(ProductStatus status);
    List<Product> findByTitleContainingIgnoreCaseAndStatus(String title, ProductStatus status);

    List<Product> findByTitleContainingIgnoreCase(String title);

}
