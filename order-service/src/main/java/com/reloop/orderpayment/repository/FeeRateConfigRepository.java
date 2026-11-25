package com.reloop.orderpayment.repository;

import com.reloop.orderpayment.entity.FeeRateConfig;
import com.reloop.orderpayment.enums.TransactionType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Database overrides of the configured fee rates, one row per transaction type. */
public interface FeeRateConfigRepository extends JpaRepository<FeeRateConfig, Long> {

    Optional<FeeRateConfig> findByTransactionType(TransactionType transactionType);
}
