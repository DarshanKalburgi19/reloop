package com.reloop.product.repository;

import com.reloop.product.entity.Store;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link Store}.
 * Query methods are derived from their names - no SQL/JPQL needed.
 */
public interface StoreRepository extends JpaRepository<Store, Long> {

    List<Store> findByOwner_Id(Long ownerId);

}
