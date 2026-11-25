package com.reloop.product.repository;

import com.reloop.product.entity.City;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link City}.
 * Query methods are derived from their names - no SQL/JPQL needed.
 */
public interface CityRepository extends JpaRepository<City, Long> {

    List<City> findByNameContainingIgnoreCase(String name);

}
