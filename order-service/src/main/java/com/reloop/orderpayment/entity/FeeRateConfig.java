package com.reloop.orderpayment.entity;

import com.reloop.orderpayment.enums.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;

/**
 * Database override of the fee rates for one transaction type. When no row exists the rates from
 * {@code application.properties} ({@code reloop.fees.*}) apply. Rates are copied onto each order/quote when it is
 * created, so changing them never alters an existing transaction.
 */
@Entity
@Table(name = "fee_rate_configs",
       uniqueConstraints = @UniqueConstraint(name = "uk_fee_rates_type", columnNames = "transaction_type"))
@org.hibernate.annotations.Check(
    name = "ck_fee_rates_range",
    constraints = "customer_fee_rate >= 0 AND customer_fee_rate <= 0.5"
            + " AND provider_commission_rate >= 0 AND provider_commission_rate <= 0.5"
)
public class FeeRateConfig extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    @Column(name = "customer_fee_rate", nullable = false, precision = 8, scale = 6)
    private BigDecimal customerFeeRate;

    @Column(name = "provider_commission_rate", nullable = false, precision = 8, scale = 6)
    private BigDecimal providerCommissionRate;

    @Column(name = "note", length = 500)
    private String note;

    public FeeRateConfig() {
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimal getCustomerFeeRate() {
        return customerFeeRate;
    }

    public void setCustomerFeeRate(BigDecimal customerFeeRate) {
        this.customerFeeRate = customerFeeRate;
    }

    public BigDecimal getProviderCommissionRate() {
        return providerCommissionRate;
    }

    public void setProviderCommissionRate(BigDecimal providerCommissionRate) {
        this.providerCommissionRate = providerCommissionRate;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
