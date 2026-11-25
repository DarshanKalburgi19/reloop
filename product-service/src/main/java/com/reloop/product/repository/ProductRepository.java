package com.reloop.product.repository;

import com.reloop.product.entity.Product;
import com.reloop.product.enums.ProductStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link Product}.
 *
 * <p>Browse/search/filter uses {@link JpaSpecificationExecutor} with
 * {@link com.reloop.product.service.ProductSpecifications}. Purchases lock the row with
 * {@link #findByIdForUpdate(Long)} so two buyers can never both buy the same item.</p>
 */
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    List<Product> findByStatus(ProductStatus status);
    List<Product> findBySeller_Id(Long sellerId);
    List<Product> findByStatusOrderByCreatedAtDesc(ProductStatus status);
    List<Product> findByTitleContainingIgnoreCaseAndStatus(String title, ProductStatus status);
    List<Product> findByTitleContainingIgnoreCase(String title);

    /** Browse page: seller, store and passport are fetched in the same query (no N+1). */
    @Override
    @EntityGraph(attributePaths = {"seller", "store", "passport"})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    /** Seller's own listings, any status, newest first. */
    @EntityGraph(attributePaths = {"store", "passport"})
    Page<Product> findBySeller_IdOrderByCreatedAtDesc(Long sellerId, Pageable pageable);

    boolean existsBySeller_Id(Long sellerId);

    /** Is there already an open (not sold / not archived) listing for this physical item? */
    boolean existsByPassport_IdAndStatusIn(Long passportId, Collection<ProductStatus> statuses);

    /** Same check, ignoring one listing (used when reactivating it). */
    boolean existsByPassport_IdAndStatusInAndIdNot(Long passportId, Collection<ProductStatus> statuses, Long excludedId);

    /** All listings of one physical item, oldest first (for the passport view). */
    List<Product> findByPassport_IdOrderByCreatedAtAscIdAsc(Long passportId);

    /** SELECT ... FOR UPDATE: serialises concurrent purchases of the same product. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}
