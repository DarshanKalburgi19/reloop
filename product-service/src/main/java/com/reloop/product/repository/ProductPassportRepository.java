package com.reloop.product.repository;

import com.reloop.product.entity.ProductPassport;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link ProductPassport}. */
public interface ProductPassportRepository extends JpaRepository<ProductPassport, Long> {

    Optional<ProductPassport> findByPassportCode(String passportCode);

    boolean existsByPassportCode(String passportCode);
}
