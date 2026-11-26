package com.reloop.orderpayment.service;

import com.reloop.orderpayment.config.FeeProperties;
import com.reloop.orderpayment.dto.fees.FeeRateView;
import com.reloop.orderpayment.dto.fees.UpdateFeeRatesRequest;
import com.reloop.orderpayment.entity.FeeRateConfig;
import com.reloop.orderpayment.enums.TransactionType;
import com.reloop.orderpayment.repository.FeeRateConfigRepository;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Where fee rates come from: a {@code fee_rate_configs} row for the transaction type if one exists (changed at
 * run time through {@code PUT /api/internal/fee-rates/{type}}), otherwise {@code reloop.fees.*} from
 * application.properties. The resolved rates are frozen onto each new order / quote.
 */
@Service
public class FeeRateService {

    public static final String SOURCE_DATABASE = "DATABASE";
    public static final String SOURCE_PROPERTIES = "APPLICATION_PROPERTIES";

    private final FeeRateConfigRepository configs;
    private final FeeProperties defaults;

    public FeeRateService(FeeRateConfigRepository configs, FeeProperties defaults) {
        this.configs = configs;
        this.defaults = defaults;
    }

    @Transactional(readOnly = true)
    public FeeRateView current(TransactionType type) {
        return configs.findByTransactionType(type)
                .map(c -> new FeeRateView(type, c.getCustomerFeeRate(), c.getProviderCommissionRate(), SOURCE_DATABASE,
                        c.getNote(), c.getUpdatedAt()))
                .orElseGet(() -> new FeeRateView(type, defaults.customerFeeRate(), defaults.providerCommissionRate(),
                        SOURCE_PROPERTIES, null, null));
    }

    /** Fee breakdown of {@code baseAmountPaise} with the rates currently in force for {@code type}. */
    @Transactional(readOnly = true)
    public FeeCalculator.Breakdown breakdown(TransactionType type, long baseAmountPaise) {
        FeeRateView rates = current(type);
        return FeeCalculator.compute(baseAmountPaise, rates.customerFeeRate(), rates.providerCommissionRate());
    }

    @Transactional(readOnly = true)
    public List<FeeRateView> all() {
        return Arrays.stream(TransactionType.values()).map(this::current).toList();
    }

    /** Creates or replaces the database override for {@code type}. Existing orders/quotes keep their frozen rates. */
    @Transactional
    public FeeRateView update(TransactionType type, UpdateFeeRatesRequest req) {
        FeeRateConfig c = configs.findByTransactionType(type).orElseGet(() -> {
            FeeRateConfig fresh = new FeeRateConfig();
            fresh.setTransactionType(type);
            return fresh;
        });
        c.setCustomerFeeRate(scale(req.customerFeeRate()));
        c.setProviderCommissionRate(scale(req.providerCommissionRate()));
        c.setNote(req.note() == null || req.note().isBlank() ? null : req.note().strip());
        configs.saveAndFlush(c);
        return current(type);
    }

    /** Removes the override so application.properties applies again. */
    @Transactional
    public FeeRateView reset(TransactionType type) {
        configs.findByTransactionType(type).ifPresent(configs::delete);
        configs.flush();
        return current(type);
    }

    private static BigDecimal scale(BigDecimal rate) {
        return rate.setScale(6, java.math.RoundingMode.HALF_UP);
    }
}
