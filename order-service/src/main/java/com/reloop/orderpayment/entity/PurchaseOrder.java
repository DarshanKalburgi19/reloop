package com.reloop.orderpayment.entity;

import com.reloop.orderpayment.enums.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A marketplace purchase (transaction type MARKETPLACE_PURCHASE). The product stays RESERVED in product-service
 * while the order is PENDING_PAYMENT; only a SUCCESS payment confirms the order and completes the sale.
 *
 * <p>Fee rates and amounts are FROZEN when the order is created and copied onto its payments:
 * {@code total = subtotal + customerFee}, {@code sellerNet = subtotal − providerCommission},
 * {@code platformRevenue = customerFee + providerCommission}.</p>
 */
@Entity
@Table(
    name = "purchase_orders",
    uniqueConstraints = @UniqueConstraint(name = "uk_orders_number", columnNames = "order_number"),
    indexes = {
        @Index(name = "ix_orders_buyer", columnList = "buyer_account_id"),
        @Index(name = "ix_orders_seller", columnList = "seller_account_id"),
        @Index(name = "ix_orders_status", columnList = "status"),
        @Index(name = "ix_orders_product", columnList = "product_external_id"),
        @Index(name = "ix_orders_expires", columnList = "status, expires_at")
    }
)
public class PurchaseOrder extends BaseEntity {

    @Column(name = "order_number", nullable = false, length = 40)
    private String orderNumber;

    @Column(name = "buyer_account_id", nullable = false)
    private Long buyerAccountId;

    @Column(name = "seller_account_id", nullable = false)
    private Long sellerAccountId;

    /** product-service Product ID (no cross-service FK); also on the order item. */
    @Column(name = "product_external_id")
    private Long productExternalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    /** Item price (seller's gross amount), paise. */
    @Column(name = "subtotal_paise", nullable = false)
    private Long subtotalPaise;

    @Column(name = "customer_fee_rate", nullable = false, precision = 8, scale = 6)
    private BigDecimal customerFeeRate;

    @Column(name = "customer_fee_paise", nullable = false)
    private Long customerFeePaise;

    /** subtotal + customer fee: what the buyer pays. */
    @Column(name = "total_paise", nullable = false)
    private Long totalPaise;

    @Column(name = "provider_commission_rate", nullable = false, precision = 8, scale = 6)
    private BigDecimal providerCommissionRate;

    @Column(name = "provider_commission_paise", nullable = false)
    private Long providerCommissionPaise;

    /** subtotal − provider commission: what the seller earns. */
    @Column(name = "seller_net_paise", nullable = false)
    private Long sellerNetPaise;

    @Column(name = "platform_revenue_paise", nullable = false)
    private Long platformRevenuePaise;

    @Column(name = "refunded_amount_paise", nullable = false)
    private Long refundedAmountPaise = 0L;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    /** Unpaid orders are cancelled (and the product released) after this moment. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** When the SUCCESS payment confirmed the order. */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    /** False while a cancelled order's product reservation still has to be released in product-service. */
    @Column(name = "product_released", nullable = false)
    private boolean productReleased = true;

    /** Reason of the most recent failed/cancelled payment attempt. */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    public PurchaseOrder() {
    }

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public Long getBuyerAccountId() {
        return buyerAccountId;
    }

    public void setBuyerAccountId(Long buyerAccountId) {
        this.buyerAccountId = buyerAccountId;
    }

    public Long getSellerAccountId() {
        return sellerAccountId;
    }

    public void setSellerAccountId(Long sellerAccountId) {
        this.sellerAccountId = sellerAccountId;
    }

    public Long getProductExternalId() {
        return productExternalId;
    }

    public void setProductExternalId(Long productExternalId) {
        this.productExternalId = productExternalId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public Long getSubtotalPaise() {
        return subtotalPaise;
    }

    public void setSubtotalPaise(Long subtotalPaise) {
        this.subtotalPaise = subtotalPaise;
    }

    public BigDecimal getCustomerFeeRate() {
        return customerFeeRate;
    }

    public void setCustomerFeeRate(BigDecimal customerFeeRate) {
        this.customerFeeRate = customerFeeRate;
    }

    public Long getCustomerFeePaise() {
        return customerFeePaise;
    }

    public void setCustomerFeePaise(Long customerFeePaise) {
        this.customerFeePaise = customerFeePaise;
    }

    public Long getTotalPaise() {
        return totalPaise;
    }

    public void setTotalPaise(Long totalPaise) {
        this.totalPaise = totalPaise;
    }

    public BigDecimal getProviderCommissionRate() {
        return providerCommissionRate;
    }

    public void setProviderCommissionRate(BigDecimal providerCommissionRate) {
        this.providerCommissionRate = providerCommissionRate;
    }

    public Long getProviderCommissionPaise() {
        return providerCommissionPaise;
    }

    public void setProviderCommissionPaise(Long providerCommissionPaise) {
        this.providerCommissionPaise = providerCommissionPaise;
    }

    public Long getSellerNetPaise() {
        return sellerNetPaise;
    }

    public void setSellerNetPaise(Long sellerNetPaise) {
        this.sellerNetPaise = sellerNetPaise;
    }

    public Long getPlatformRevenuePaise() {
        return platformRevenuePaise;
    }

    public void setPlatformRevenuePaise(Long platformRevenuePaise) {
        this.platformRevenuePaise = platformRevenuePaise;
    }

    public Long getRefundedAmountPaise() {
        return refundedAmountPaise;
    }

    public void setRefundedAmountPaise(Long refundedAmountPaise) {
        this.refundedAmountPaise = refundedAmountPaise;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public boolean isProductReleased() {
        return productReleased;
    }

    public void setProductReleased(boolean productReleased) {
        this.productReleased = productReleased;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public LocalDateTime getRefundedAt() {
        return refundedAt;
    }

    public void setRefundedAt(LocalDateTime refundedAt) {
        this.refundedAt = refundedAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }
}
