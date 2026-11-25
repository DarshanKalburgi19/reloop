package com.reloop.orderpayment.repository;

import com.reloop.orderpayment.entity.ProviderPayout;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.reloop.orderpayment.enums.PayoutStatus;

/**
 * Spring Data JPA repository for {@link ProviderPayout}.
 * Query methods are derived from their names - no SQL/JPQL needed.
 */
public interface ProviderPayoutRepository extends JpaRepository<ProviderPayout, Long> {

    Optional<ProviderPayout> findByPaymentTransactionId(Long paymentTransactionId);
    List<ProviderPayout> findByProviderId(Long providerId);
    List<ProviderPayout> findByStatus(PayoutStatus status);

}
