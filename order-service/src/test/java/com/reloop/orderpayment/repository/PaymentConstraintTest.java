package com.reloop.orderpayment.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.reloop.orderpayment.entity.CommissionRecord;
import com.reloop.orderpayment.entity.PaymentTransaction;
import com.reloop.orderpayment.entity.PurchaseOrder;
import com.reloop.orderpayment.enums.OrderStatus;
import com.reloop.orderpayment.enums.PaymentMethod;
import com.reloop.orderpayment.enums.PaymentStatus;
import com.reloop.orderpayment.enums.ProviderType;
import com.reloop.orderpayment.enums.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the order-service money schema on H2:
 * unique transaction IDs (never duplicated), the "exactly one payment
 * reference" CHECK constraint, unique order numbers, and frozen commission
 * rates. Each test ends on its single expected violation, because a failed
 * flush marks the transaction rollback-only.
 */
@SpringBootTest
@Transactional
class PaymentConstraintTest {

    @Autowired
    private PurchaseOrderRepository orders;

    @Autowired
    private PaymentTransactionRepository payments;

    @Autowired
    private CommissionRecordRepository commissions;

    private PurchaseOrder newOrder(String orderNumber) {
        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNumber(orderNumber);
        order.setBuyerAccountId(101L);
        order.setSellerAccountId(201L);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setSubtotalPaise(100000L);   // ₹1,000.00
        order.setCustomerFeeRate(new BigDecimal("0.0200"));
        order.setCustomerFeePaise(2000L);  // 2% customer fee
        order.setTotalPaise(102000L);      // ₹1,020.00
        order.setProviderCommissionRate(new BigDecimal("0.0200"));
        order.setProviderCommissionPaise(2000L);  // 2% seller commission
        order.setSellerNetPaise(98000L);          // ₹980.00
        order.setPlatformRevenuePaise(4000L);     // ₹40.00
        return order;
    }

    private PaymentTransaction newPayment(String transactionId, Long orderId) {
        PaymentTransaction payment = new PaymentTransaction();
        payment.setTransactionId(transactionId);
        payment.setPurchaseOrderId(orderId);
        payment.setBuyerAccountId(101L);
        payment.setProviderId(201L);
        payment.setProviderType(ProviderType.SELLER);
        payment.setMethod(PaymentMethod.UPI);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setBaseAmountPaise(100000L);
        payment.setCustomerFeeRate(new BigDecimal("0.0200"));
        payment.setCustomerFeePaise(2000L);
        payment.setTotalChargedPaise(102000L);
        payment.setTransactionType(TransactionType.MARKETPLACE_PURCHASE);
        payment.setProviderAccountId(201L);
        payment.setProviderCommissionRate(new BigDecimal("0.0200"));
        payment.setProviderCommissionPaise(2000L);
        payment.setProviderNetPaise(98000L);
        payment.setPlatformRevenuePaise(4000L);
        payment.setAttemptedAt(LocalDateTime.now());
        payment.setCompletedAt(LocalDateTime.now());
        return payment;
    }

    @Test
    void successfulPaymentKeepsFrozenRatesAndCreatesCommissionLedger() {
        PurchaseOrder order = orders.saveAndFlush(newOrder("ORD-TEST-0001"));

        PaymentTransaction payment = payments.saveAndFlush(newPayment("TXN-TEST-0001", order.getId()));

        assertNotNull(payment.getId());
        assertTrue(payments.findByTransactionId("TXN-TEST-0001").isPresent());
        assertTrue(payments.existsByTransactionId("TXN-TEST-0001"));

        // Rates are frozen on the transaction, not looked up later.
        assertEquals(0, new BigDecimal("0.0200").compareTo(payment.getCustomerFeeRate()));
        assertEquals(102000L, payment.getTotalChargedPaise());
        assertEquals(98000L, payment.getProviderNetPaise());
        assertEquals(4000L, payment.getPlatformRevenuePaise());
        assertEquals(1, payments.findByStatus(PaymentStatus.SUCCESS).size());

        // Commission ledger: 2% + 2% = ₹40 on a ₹1,000 repair/order.
        CommissionRecord commission = new CommissionRecord();
        commission.setPaymentTransactionId(payment.getId());
        commission.setTransactionId(payment.getTransactionId());
        commission.setTransactionType(TransactionType.MARKETPLACE_PURCHASE);
        commission.setReferenceType("PURCHASE_ORDER");
        commission.setReferenceId(order.getId());
        commission.setCustomerFeeRate(new BigDecimal("0.0200"));
        commission.setCustomerFeePaise(2000L);
        commission.setProviderCommissionRate(new BigDecimal("0.0200"));
        commission.setProviderCommissionPaise(2000L);
        commission.setTotalCommissionPaise(4000L);
        commissions.saveAndFlush(commission);

        assertTrue(commissions.findByTransactionId("TXN-TEST-0001").isPresent());
        assertEquals(4000L, commissions.findByTransactionId("TXN-TEST-0001").get().getTotalCommissionPaise());
    }

    @Test
    void duplicateTransactionIdIsRejectedByTheDatabase() {
        PurchaseOrder order = orders.saveAndFlush(newOrder("ORD-TEST-0002"));
        payments.saveAndFlush(newPayment("TXN-DUP-0001", order.getId()));

        // Requirement: duplicate transaction IDs must be impossible.
        assertThrows(DataIntegrityViolationException.class, () ->
                payments.saveAndFlush(newPayment("TXN-DUP-0001", order.getId())));
    }

    @Test
    void secondSuccessfulPaymentForTheSameOrderIsRejectedBySettlementKey() {
        PurchaseOrder order = orders.saveAndFlush(newOrder("ORD-TEST-0004"));
        PaymentTransaction first = newPayment("TXN-SET-0001", order.getId());
        first.setSettlementKey("ORDER-" + order.getId());
        payments.saveAndFlush(first);

        // Requirement: duplicate payment processing must be impossible, even if the service check is bypassed.
        PaymentTransaction second = newPayment("TXN-SET-0002", order.getId());
        second.setSettlementKey("ORDER-" + order.getId());
        assertThrows(DataIntegrityViolationException.class, () -> payments.saveAndFlush(second));
    }

    @Test
    void failedAttemptsHaveNoSettlementKeySoAnyNumberCanBeRecorded() {
        PurchaseOrder order = orders.saveAndFlush(newOrder("ORD-TEST-0005"));
        for (int i = 1; i <= 3; i++) {
            PaymentTransaction failed = newPayment("TXN-FAIL-000" + i, order.getId());
            failed.setStatus(PaymentStatus.FAILED);
            payments.saveAndFlush(failed);
        }
        assertEquals(3, payments.findByPurchaseOrderIdOrderByIdAsc(order.getId()).size());
    }

    @Test
    void paymentMustReferenceExactlyOneTarget() {
        // Neither order nor quote -> CHECK constraint violation.
        PaymentTransaction orphan = newPayment("TXN-BAD-0001", null);
        assertThrows(DataIntegrityViolationException.class, () -> payments.saveAndFlush(orphan));
    }

    @Test
    void duplicateOrderNumberIsRejectedByTheDatabase() {
        orders.saveAndFlush(newOrder("ORD-TEST-0003"));

        assertThrows(DataIntegrityViolationException.class, () ->
                orders.saveAndFlush(newOrder("ORD-TEST-0003")));
    }
}
