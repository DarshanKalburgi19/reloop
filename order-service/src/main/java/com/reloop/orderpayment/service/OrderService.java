package com.reloop.orderpayment.service;

import com.reloop.orderpayment.client.ProductSale;
import com.reloop.orderpayment.client.ProductServiceClient;
import com.reloop.orderpayment.config.OrderProperties;
import com.reloop.orderpayment.dto.order.OrderView;
import com.reloop.orderpayment.entity.OrderItem;
import com.reloop.orderpayment.entity.PaymentTransaction;
import com.reloop.orderpayment.entity.PurchaseOrder;
import com.reloop.orderpayment.enums.OrderStatus;
import com.reloop.orderpayment.enums.TransactionType;
import com.reloop.orderpayment.exception.ConflictException;
import com.reloop.orderpayment.exception.NotFoundException;
import com.reloop.orderpayment.exception.ProductServiceException;
import com.reloop.orderpayment.repository.PaymentTransactionRepository;
import com.reloop.orderpayment.repository.PurchaseOrderRepository;
import com.reloop.orderpayment.security.AuthenticatedAccount;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Marketplace orders (transaction type MARKETPLACE_PURCHASE).
 *
 * <pre>
 * POST /api/orders {productId}
 *   → product-service reserves the listing (ACTIVE → RESERVED) and returns price + seller
 *   → order PENDING_PAYMENT with FROZEN rates: total = price + customer fee; seller net = price − commission
 * POST /api/payments/simulate {orderId, amountPaise = total}
 *   → SUCCESS: order CONFIRMED, product SOLD (passport moves to the buyer)
 *   → FAILED / CANCELLED: order stays PENDING_PAYMENT until it expires
 * POST /api/orders/{id}/cancel, or the payment window passes
 *   → order CANCELLED and the reservation released (RESERVED → ACTIVE)
 * </pre>
 * The order is the buyer's; the seller can read it too (404 for anybody else).
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final PurchaseOrderRepository orders;
    private final PaymentTransactionRepository payments;
    private final ProductServiceClient productService;
    private final FeeRateService feeRates;
    private final ReferenceGenerator references;
    private final OrderProperties properties;
    private final TransactionTemplate tx;
    private final Clock clock;

    public OrderService(PurchaseOrderRepository orders, PaymentTransactionRepository payments,
                        ProductServiceClient productService, FeeRateService feeRates, ReferenceGenerator references,
                        OrderProperties properties, TransactionTemplate tx, Clock clock) {
        this.orders = orders;
        this.payments = payments;
        this.productService = productService;
        this.feeRates = feeRates;
        this.references = references;
        this.properties = properties;
        this.tx = tx;
        this.clock = clock;
    }

    /** Result of {@link #create}; {@code existing} = the buyer already had an open order for this product. */
    public record Created(OrderView order, boolean existing) {
    }

    /**
     * Places an order. Not one database transaction on purpose: the product is reserved over HTTP first, then the
     * order is saved; if saving fails the reservation is released again (compensation).
     */
    public Created create(AuthenticatedAccount buyer, Long productId) {
        OrderView open = tx.execute(status -> orders.findFirstByBuyerAccountIdAndProductExternalIdAndStatus(
                buyer.accountId(), productId, OrderStatus.PENDING_PAYMENT)
                .filter(o -> !isExpired(o)).map(this::view).orElse(null));
        if (open != null) {
            return new Created(open, true);
        }
        // an expired open order of this buyer still holds the reservation: cancel it and release first
        tx.execute(status -> orders.findFirstByBuyerAccountIdAndProductExternalIdAndStatus(buyer.accountId(), productId,
                OrderStatus.PENDING_PAYMENT).map(PurchaseOrder::getId)).ifPresent(this::expireIfDue);

        String orderNumber = uniqueOrderNumber();
        ProductSale sale = productService.reserve(productId, buyer.accountId(), orderNumber);
        if (sale.pricePaise() <= 0 || sale.sellerAccountId() == null) {
            release(productId, orderNumber, "invalid product data");
            throw new ProductServiceException(502, "Product service returned an invalid price or seller", null);
        }
        if (Objects.equals(sale.sellerAccountId(), buyer.accountId())) {
            release(productId, orderNumber, "own listing");
            throw new com.reloop.orderpayment.exception.ForbiddenException("You cannot purchase your own listing");
        }
        try {
            OrderView saved = tx.execute(status -> view(orders.saveAndFlush(newOrder(buyer, productId, orderNumber, sale))));
            return new Created(saved, false);
        } catch (RuntimeException ex) {
            release(productId, orderNumber, "order could not be saved");
            throw ex;
        }
    }

    private PurchaseOrder newOrder(AuthenticatedAccount buyer, Long productId, String orderNumber, ProductSale sale) {
        FeeCalculator.Breakdown b = feeRates.breakdown(TransactionType.MARKETPLACE_PURCHASE, sale.pricePaise());
        LocalDateTime now = LocalDateTime.now(clock);
        PurchaseOrder o = new PurchaseOrder();
        o.setOrderNumber(orderNumber);
        o.setBuyerAccountId(buyer.accountId());
        o.setSellerAccountId(sale.sellerAccountId());
        o.setProductExternalId(productId);
        o.setStatus(OrderStatus.PENDING_PAYMENT);
        o.setSubtotalPaise(b.baseAmountPaise());
        o.setCustomerFeeRate(b.customerFeeRate());
        o.setCustomerFeePaise(b.customerFeePaise());
        o.setTotalPaise(b.totalPayablePaise());
        o.setProviderCommissionRate(b.providerCommissionRate());
        o.setProviderCommissionPaise(b.providerCommissionPaise());
        o.setSellerNetPaise(b.providerNetPaise());
        o.setPlatformRevenuePaise(b.platformRevenuePaise());
        o.setCurrency(Money.CURRENCY);
        o.setExpiresAt(now.plusMinutes(properties.paymentWindowMinutes()));
        o.setProductReleased(true);

        OrderItem item = new OrderItem();
        item.setProductExternalId(productId);
        String title = sale.title() == null || sale.title().isBlank() ? "Product #" + productId : sale.title();
        item.setProductTitleSnapshot(title.length() > 200 ? title.substring(0, 200) : title);
        item.setUnitPricePaise(sale.pricePaise());
        item.setQuantity(1);
        item.setLineTotalPaise(sale.pricePaise());
        o.addItem(item);
        return o;
    }

    @Transactional(readOnly = true)
    public OrderView get(AuthenticatedAccount caller, Long orderId) {
        return view(visible(caller, orderId));
    }

    /** The caller's orders as buyer ({@code role=buyer}, default) or seller ({@code role=seller}), newest first. */
    @Transactional(readOnly = true)
    public List<OrderView> list(AuthenticatedAccount caller, String role) {
        List<PurchaseOrder> found = "seller".equalsIgnoreCase(role)
                ? orders.findBySellerAccountIdOrderByIdDesc(caller.accountId())
                : orders.findByBuyerAccountIdOrderByIdDesc(caller.accountId());
        return found.stream().map(this::view).toList();
    }

    /** Buyer cancels an unpaid order; the product reservation is released. Paid orders need a refund instead. */
    public OrderView cancel(AuthenticatedAccount caller, Long orderId, String reason) {
        PurchaseOrder cancelled = tx.execute(status -> {
            PurchaseOrder o = orders.findByIdForUpdate(orderId)
                    .filter(x -> Objects.equals(x.getBuyerAccountId(), caller.accountId()))
                    .orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));
            if (o.getStatus() == OrderStatus.CANCELLED) {
                return o;
            }
            if (o.getStatus() != OrderStatus.PENDING_PAYMENT) {
                throw new ConflictException("Order " + o.getOrderNumber() + " is " + o.getStatus()
                        + " and can't be cancelled; ask the seller for a refund instead",
                        Map.of("orderStatus", o.getStatus().name()));
            }
            markCancelled(o, reason == null || reason.isBlank() ? "Cancelled by the buyer" : reason.strip());
            return o;
        });
        releaseAndRecord(cancelled);
        return tx.execute(status -> view(orders.findById(orderId).orElseThrow()));
    }

    /**
     * Cancels unpaid orders past their payment window and retries releases that failed earlier.
     * Called by {@link OrderExpiryJob}; returns how many orders were cancelled.
     */
    public int expireDueOrders() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> due = tx.execute(status -> orders
                .findTop50ByStatusAndExpiresAtBeforeOrderByIdAsc(OrderStatus.PENDING_PAYMENT, now)
                .stream().map(PurchaseOrder::getId).toList());
        int cancelled = 0;
        for (Long id : due) {
            if (expireIfDue(id)) {
                cancelled++;
            }
        }
        List<PurchaseOrder> pending = tx.execute(status ->
                orders.findTop50ByStatusAndProductReleasedFalseOrderByIdAsc(OrderStatus.CANCELLED));
        for (PurchaseOrder o : pending) {
            releaseAndRecord(o);
        }
        return cancelled;
    }

    /** Cancels one unpaid order if its payment window has passed, then releases its product. */
    boolean expireIfDue(Long orderId) {
        PurchaseOrder o = tx.execute(status -> {
            PurchaseOrder locked = orders.findByIdForUpdate(orderId).orElse(null);
            if (locked == null || locked.getStatus() != OrderStatus.PENDING_PAYMENT || !isExpired(locked)) {
                return null;
            }
            markCancelled(locked, "Payment window expired");
            return locked;
        });
        if (o == null) {
            return false;
        }
        releaseAndRecord(o);
        return true;
    }

    private void markCancelled(PurchaseOrder o, String reason) {
        o.setStatus(OrderStatus.CANCELLED);
        o.setCancelledAt(LocalDateTime.now(clock));
        o.setCancelReason(reason.length() > 500 ? reason.substring(0, 500) : reason);
        o.setProductReleased(false);
    }

    /** Releases the reservation; if product-service is down the order is retried by the next expiry sweep. */
    private void releaseAndRecord(PurchaseOrder o) {
        if (o.isProductReleased()) {
            return;
        }
        if (release(o.getProductExternalId(), o.getOrderNumber(), o.getCancelReason())) {
            tx.executeWithoutResult(status -> orders.findById(o.getId()).ifPresent(x -> x.setProductReleased(true)));
        }
    }

    private boolean release(Long productId, String orderNumber, String reason) {
        try {
            productService.release(productId, orderNumber, reason);
            return true;
        } catch (RuntimeException ex) {
            log.warn("Could not release product {} for order {}: {}", productId, orderNumber, ex.getMessage());
            return false;
        }
    }

    private boolean isExpired(PurchaseOrder o) {
        return o.getExpiresAt() != null && LocalDateTime.now(clock).isAfter(o.getExpiresAt());
    }

    private PurchaseOrder visible(AuthenticatedAccount caller, Long orderId) {
        return orders.findById(orderId)
                .filter(o -> Objects.equals(o.getBuyerAccountId(), caller.accountId())
                        || Objects.equals(o.getSellerAccountId(), caller.accountId()))
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));
    }

    OrderView view(PurchaseOrder o) {
        List<OrderView.Item> items = o.getItems().stream().map(i -> new OrderView.Item(i.getProductExternalId(),
                i.getProductTitleSnapshot(), i.getUnitPricePaise(), i.getQuantity(), i.getLineTotalPaise())).toList();
        List<PaymentTransaction> attempts = o.getId() == null ? List.of() : payments.findByPurchaseOrderIdOrderByIdAsc(o.getId());
        List<OrderView.PaymentAttempt> paymentViews = attempts.stream().map(p -> new OrderView.PaymentAttempt(
                p.getTransactionId(), p.getStatus().name(), p.getMethod().name(), p.getTotalChargedPaise(),
                p.getFailureReason(), p.getAttemptedAt())).toList();
        String title = items.isEmpty() ? null : items.get(0).title();
        boolean due = o.getStatus() == OrderStatus.PENDING_PAYMENT && !isExpired(o);
        return new OrderView(o.getId(), o.getOrderNumber(), TransactionType.MARKETPLACE_PURCHASE.name(),
                o.getStatus().name(), o.getBuyerAccountId(), o.getSellerAccountId(), o.getProductExternalId(), title,
                items, o.getSubtotalPaise(), o.getCustomerFeeRate(), o.getCustomerFeePaise(), o.getTotalPaise(),
                o.getProviderCommissionRate(), o.getProviderCommissionPaise(), o.getSellerNetPaise(),
                o.getPlatformRevenuePaise(), o.getRefundedAmountPaise(), due ? o.getTotalPaise() : 0, o.getCurrency(),
                o.getCreatedAt(), o.getExpiresAt(), o.getPaidAt(), o.getCancelledAt(), o.getCancelReason(),
                o.getFailureReason(), paymentViews);
    }

    private String uniqueOrderNumber() {
        for (int i = 0; i < 5; i++) {
            String n = references.next("ORD");
            if (orders.findByOrderNumber(n).isEmpty()) {
                return n;
            }
        }
        throw new IllegalStateException("could not generate a unique order number");
    }
}
