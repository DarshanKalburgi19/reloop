package com.reloop.product.repository;

import com.reloop.product.entity.Address;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link Address}.
 * Query methods are derived from their names - no SQL/JPQL needed.
 */
public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByAccount_Id(Long accountId);

}
