package com.reloop.orderpayment.repository;

import com.reloop.orderpayment.entity.ProviderPayout;
import com.reloop.orderpayment.enums.PayoutStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link ProviderPayout}.
 */
public interface ProviderPayoutRepository extends JpaRepository<ProviderPayout, Long> {

    Optional<ProviderPayout> findByPaymentTransactionId(Long paymentTransactionId);

    List<ProviderPayout> findByProviderId(Long providerId);

    List<ProviderPayout> findByProviderAccountIdOrderByIdDesc(Long providerAccountId);

    List<ProviderPayout> findByStatus(PayoutStatus status);
}
