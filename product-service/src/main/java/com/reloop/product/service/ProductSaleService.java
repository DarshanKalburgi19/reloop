package com.reloop.product.service;

import com.reloop.product.dto.sale.ProductReservationRequest;
import com.reloop.product.dto.sale.ProductSaleResponse;
import com.reloop.product.entity.Account;
import com.reloop.product.entity.Product;
import com.reloop.product.entity.ProductHistoryEvent;
import com.reloop.product.enums.AccountStatus;
import com.reloop.product.enums.ProductHistoryEventType;
import com.reloop.product.enums.ProductStatus;
import com.reloop.product.exception.FieldValidationException;
import com.reloop.product.exception.ListingStateException;
import com.reloop.product.exception.OwnProductPurchaseException;
import com.reloop.product.exception.ProductNotAvailableException;
import com.reloop.product.exception.ProductNotFoundException;
import com.reloop.product.repository.AccountRepository;
import com.reloop.product.repository.ProductHistoryEventRepository;
import com.reloop.product.repository.ProductRepository;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marketplace sales driven by order-service (internal API). Every call locks the product row and is idempotent for
 * the same {@code orderReference}:
 *
 * <pre>
 * reserve       ACTIVE → RESERVED (held for the order; hidden from browse, frozen for the seller)
 * confirm-sale  RESERVED by this order → SOLD; buyer set, passport ownership moves to the buyer, SOLD history event
 * release       RESERVED by this order → ACTIVE (order cancelled / expired); no-op in any other state
 * </pre>
 * Errors: 404 unknown product, 403 buyer is the seller, 409 not available / reserved by another order.
 */
@Service
public class ProductSaleService {

    private final ProductRepository products;
    private final AccountRepository accounts;
    private final ProductHistoryEventRepository history;

    public ProductSaleService(ProductRepository products, AccountRepository accounts, ProductHistoryEventRepository history) {
        this.products = products;
        this.accounts = accounts;
        this.history = history;
    }

    @Transactional
    public ProductSaleResponse reserve(Long productId, ProductReservationRequest req) {
        Account buyer = buyer(req);
        Product product = products.findByIdForUpdate(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        if (Objects.equals(product.getSeller().getId(), buyer.getId())) {
            throw new OwnProductPurchaseException();
        }
        if (product.getStatus() == ProductStatus.RESERVED) {
            if (req.orderReference().equals(product.getReservationReference())
                    && Objects.equals(product.getReservedByAccountId(), buyer.getId())) {
                return response(product);   // repeated call for the same order
            }
            throw new ListingStateException("This product is reserved by another buyer's order; try again later");
        }
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new ProductNotAvailableException(product.getStatus());
        }
        product.setStatus(ProductStatus.RESERVED);
        product.setReservedByAccountId(buyer.getId());
        product.setReservationReference(req.orderReference());
        product.setReservedAt(LocalDateTime.now());
        record(product, ProductHistoryEventType.RESERVED, buyer, "Reserved by order " + req.orderReference()
                + " for " + Money.format(product.getPricePaise()) + " while payment is pending");
        return response(product);
    }

    @Transactional
    public ProductSaleResponse confirmSale(Long productId, ProductReservationRequest req) {
        Account buyer = buyer(req);
        Product product = products.findByIdForUpdate(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        boolean sameOrder = req.orderReference().equals(product.getReservationReference());
        if (product.getStatus() == ProductStatus.SOLD && sameOrder && product.getBuyer() != null
                && Objects.equals(product.getBuyer().getId(), buyer.getId())) {
            return response(product);   // already sold to this order (retry)
        }
        if (product.getStatus() != ProductStatus.RESERVED || !sameOrder
                || !Objects.equals(product.getReservedByAccountId(), buyer.getId())) {
            throw new ListingStateException("Product " + productId + " is not reserved for order " + req.orderReference()
                    + " (availability: " + product.getStatus() + ")");
        }
        LocalDateTime now = LocalDateTime.now();
        product.setStatus(ProductStatus.SOLD);
        product.setSoldAt(now);
        product.setBuyer(buyer);
        product.setReservedByAccountId(null);
        product.getPassport().setCurrentOwner(buyer);
        String txn = req.transactionId() == null ? "" : ", transaction " + req.transactionId();
        record(product, ProductHistoryEventType.SOLD, buyer, "Sold for " + Money.format(product.getPricePaise())
                + " via order " + req.orderReference() + txn + "; passport ownership transferred to the buyer");
        return response(product);
    }

    @Transactional
    public ProductSaleResponse release(Long productId, ProductReservationRequest req) {
        Product product = products.findByIdForUpdate(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        if (product.getStatus() != ProductStatus.RESERVED || !req.orderReference().equals(product.getReservationReference())) {
            return response(product);   // not held by this order (already released, sold or never reserved)
        }
        product.setStatus(ProductStatus.ACTIVE);
        product.setReservedByAccountId(null);
        product.setReservationReference(null);
        product.setReservedAt(null);
        String why = req.reason() == null || req.reason().isBlank() ? "" : ": " + req.reason().strip();
        record(product, ProductHistoryEventType.RESERVATION_RELEASED, null,
                "Reservation by order " + req.orderReference() + " released" + why);
        return response(product);
    }

    private Account buyer(ProductReservationRequest req) {
        if (req.buyerAccountId() == null) {
            throw new FieldValidationException("buyerAccountId", "must not be null");
        }
        Account buyer = accounts.findById(req.buyerAccountId())
                .orElseThrow(() -> new ListingStateException("Buyer account " + req.buyerAccountId() + " does not exist"));
        if (buyer.getStatus() != AccountStatus.ACTIVE) {
            throw new ListingStateException("Buyer account is " + buyer.getStatus() + " and can't purchase");
        }
        return buyer;
    }

    private void record(Product product, ProductHistoryEventType type, Account actor, String details) {
        String safe = details.length() > 500 ? details.substring(0, 497) + "..." : details;
        history.save(new ProductHistoryEvent(product.getPassport(), product, type, actor, product.getPricePaise(), safe));
    }

    private static ProductSaleResponse response(Product p) {
        return new ProductSaleResponse(p.getId(), p.getTitle(), p.getSeller().getId(),
                p.getBuyer() != null ? p.getBuyer().getId() : p.getReservedByAccountId(), p.getPricePaise(), p.getStatus(),
                p.getPassport().getPassportCode(), p.getReservationReference());
    }
}
