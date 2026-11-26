package com.reloop.orderpayment.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.reloop.orderpayment.entity.CommissionRecord;
import com.reloop.orderpayment.entity.LedgerEntry;
import com.reloop.orderpayment.entity.PaymentTransaction;
import com.reloop.orderpayment.entity.ProviderPayout;
import com.reloop.orderpayment.entity.PurchaseOrder;
import com.reloop.orderpayment.enums.LedgerAccount;
import com.reloop.orderpayment.enums.LedgerDirection;
import com.reloop.orderpayment.enums.OrderStatus;
import com.reloop.orderpayment.enums.PaymentMethod;
import com.reloop.orderpayment.enums.PaymentStatus;
import com.reloop.orderpayment.enums.PayoutStatus;
import com.reloop.orderpayment.enums.ProviderType;
import com.reloop.orderpayment.enums.TransactionType;
import com.reloop.orderpayment.exception.ProductServiceException;
import com.reloop.orderpayment.support.ApiTestBase;
import com.reloop.orderpayment.support.Tokens;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;

/**
 * POST /api/payments/simulate for MARKETPLACE_PURCHASE orders: success, failed, cancelled, incorrect amount,
 * duplicates and idempotency, rollback when product-service refuses, and no card data ever stored.
 */
class MarketplacePaymentApiTest extends ApiTestBase {

    @Test
    void successfulPaymentConfirmsTheOrderAndRecordsCommissionPayoutAndLedger() throws Exception {
        JsonNode order = placeOrder(BUYER, PRODUCT);
        long orderId = order.get("id").asLong();

        JsonNode t = body(payOrder(BUYER, orderId, "UPI", "SUCCESS", 102_000)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replay", "false"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.transactionType").value("MARKETPLACE_PURCHASE"))
                .andExpect(jsonPath("$.purchaseOrderId").value(orderId))
                .andExpect(jsonPath("$.orderNumber").value(order.get("orderNumber").asText()))
                .andExpect(jsonPath("$.productId").value(PRODUCT))
                .andExpect(jsonPath("$.providerType").value("SELLER"))
                .andExpect(jsonPath("$.providerAccountId").value(SELLER))
                .andExpect(jsonPath("$.baseAmountPaise").value(100000))
                .andExpect(jsonPath("$.customerFeePaise").value(2000))
                .andExpect(jsonPath("$.totalChargedPaise").value(102000))
                .andExpect(jsonPath("$.providerCommissionPaise").value(2000))
                .andExpect(jsonPath("$.providerNetPaise").value(98000))
                .andExpect(jsonPath("$.platformRevenuePaise").value(4000))
                .andExpect(jsonPath("$.currentProviderNetPaise").value(98000))
                .andExpect(jsonPath("$.payoutStatus").value("PENDING"))
                .andExpect(jsonPath("$.commissionStatus").value("RECORDED"))
                .andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.containsString("Simulated"))));

        String txn = t.get("transactionId").asText();
        verify(productService).confirmSale(PRODUCT, BUYER, order.get("orderNumber").asText(), txn);

        PurchaseOrder o = orders.findById(orderId).orElseThrow();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(o.getPaidAt()).isNotNull();

        PaymentTransaction p = payments.findByTransactionId(txn).orElseThrow();
        assertThat(p.getTransactionType()).isEqualTo(TransactionType.MARKETPLACE_PURCHASE);
        assertThat(p.getProviderType()).isEqualTo(ProviderType.SELLER);
        assertThat(p.getMethod()).isEqualTo(PaymentMethod.UPI);
        assertThat(p.getCustomerFeeRate()).isEqualByComparingTo("0.02");
        assertThat(p.getProviderCommissionRate()).isEqualByComparingTo("0.02");
        assertThat(p.getSettlementKey()).isEqualTo("ORDER-" + orderId);

        CommissionRecord c = commissions.findByPaymentTransactionId(p.getId()).orElseThrow();
        assertThat(c.getTransactionType()).isEqualTo(TransactionType.MARKETPLACE_PURCHASE);
        assertThat(c.getReferenceType()).isEqualTo("PURCHASE_ORDER");
        assertThat(c.getCustomerFeePaise()).isEqualTo(2_000);
        assertThat(c.getProviderCommissionPaise()).isEqualTo(2_000);
        assertThat(c.getTotalCommissionPaise()).isEqualTo(4_000);

        ProviderPayout payout = payouts.findByPaymentTransactionId(p.getId()).orElseThrow();
        assertThat(payout.getProviderAccountId()).isEqualTo(SELLER);
        assertThat(payout.getGrossAmountPaise()).isEqualTo(100_000);
        assertThat(payout.getNetAmountPaise()).isEqualTo(98_000);
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PENDING);

        List<LedgerEntry> lines = ledger.findByPaymentTransactionIdOrderByIdAsc(p.getId());
        assertThat(lines).extracting(LedgerEntry::getAccount).containsExactly(LedgerAccount.CUSTOMER_PAYMENTS,
                LedgerAccount.PROVIDER_PAYABLE, LedgerAccount.PLATFORM_FEE_REVENUE, LedgerAccount.PLATFORM_COMMISSION_REVENUE);
        assertThat(lines).extracting(LedgerEntry::getAmountPaise).containsExactly(102_000L, 98_000L, 2_000L, 2_000L);
        long debits = lines.stream().filter(e -> e.getDirection() == LedgerDirection.DEBIT).mapToLong(LedgerEntry::getAmountPaise).sum();
        long credits = lines.stream().filter(e -> e.getDirection() == LedgerDirection.CREDIT).mapToLong(LedgerEntry::getAmountPaise).sum();
        assertThat(debits).isEqualTo(credits).isEqualTo(102_000);
    }

    @Test
    void failedPaymentCreatesNoPayoutAndTheOrderStaysPayable() throws Exception {
        long orderId = placeOrder(BUYER, PRODUCT).get("id").asLong();

        payOrder(BUYER, orderId, "CARD", "FAILED", 102_000)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value(org.hamcrest.Matchers.containsString("declined")))
                .andExpect(jsonPath("$.payoutReference").doesNotExist())
                .andExpect(jsonPath("$.commissionTransactionId").doesNotExist());

        assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(commissions.count()).isZero();
        assertThat(payouts.count()).isZero();
        assertThat(ledger.count()).isZero();
        verify(productService, never()).confirmSale(anyLong(), anyLong(), anyString(), anyString());

        // the order shows the failed attempt and can still be paid
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/orders/{id}", orderId)
                        .header("Authorization", Tokens.customer(BUYER)))
                .andExpect(jsonPath("$.payments[0].status").value("FAILED"))
                .andExpect(jsonPath("$.lastPaymentFailure").exists())
                .andExpect(jsonPath("$.amountDuePaise").value(102000));
        payOrder(BUYER, orderId, "UPI", "SUCCESS", 102_000).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        assertThat(payouts.count()).isEqualTo(1);
    }

    @Test
    void cancelledPaymentIsRecordedWithoutChargingAnything() throws Exception {
        long orderId = placeOrder(BUYER, PRODUCT).get("id").asLong();

        payOrder(BUYER, orderId, "NET_BANKING", "CANCELLED", 102_000)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.failureReason").value(org.hamcrest.Matchers.containsString("cancelled")))
                .andExpect(jsonPath("$.currentPlatformRevenuePaise").value(0));

        assertThat(payments.findByPurchaseOrderIdOrderByIdAsc(orderId)).extracting(PaymentTransaction::getStatus)
                .containsExactly(PaymentStatus.CANCELLED);
        assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(payouts.count()).isZero();
        assertThat(ledger.count()).isZero();
    }

    @ParameterizedTest
    @EnumSource(PaymentMethod.class)
    void everySimulatedMethodCanPay(PaymentMethod method) throws Exception {
        long orderId = placeOrder(BUYER, PRODUCT).get("id").asLong();
        payOrder(BUYER, orderId, method.name(), "SUCCESS", 102_000).andExpect(status().isCreated())
                .andExpect(jsonPath("$.method").value(method.name()));
    }

    @Test
    void incorrectAmountIsRejectedWith422AndNothingIsRecorded() throws Exception {
        long orderId = placeOrder(BUYER, PRODUCT).get("id").asLong();

        for (long wrong : new long[] {100_000, 101_999, 102_001, 1}) {
            payOrder(BUYER, orderId, "UPI", "SUCCESS", wrong)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.expectedAmountPaise").value(102000))
                    .andExpect(jsonPath("$.receivedAmountPaise").value(wrong));
        }
        assertThat(payments.count()).isZero();
        assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verify(productService, never()).confirmSale(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void duplicatePaymentOfAPaidOrderIsRejected() throws Exception {
        long orderId = placeOrder(BUYER, PRODUCT).get("id").asLong();
        String txn = body(payOrder(BUYER, orderId, "UPI", "SUCCESS", 102_000).andExpect(status().isCreated()))
                .get("transactionId").asText();

        payOrder(BUYER, orderId, "CARD", "SUCCESS", 102_000)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.transactionId").value(txn))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already paid")));
        // even a "failed" retry is refused once the order is paid
        payOrder(BUYER, orderId, "CARD", "FAILED", 102_000).andExpect(status().isConflict());

        assertThat(payments.findByPurchaseOrderIdOrderByIdAsc(orderId)).hasSize(1);
        assertThat(commissions.count()).isEqualTo(1);
        assertThat(payouts.count()).isEqualTo(1);
        verify(productService, times(1)).confirmSale(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void idempotencyKeyReturnsTheOriginalAttemptInsteadOfPayingTwice() throws Exception {
        long orderId = placeOrder(BUYER, PRODUCT).get("id").asLong();
        String body = "{\"orderId\":%d,\"method\":\"UPI\",\"amountPaise\":102000}".formatted(orderId);
        var request = post("/api/payments/simulate").header("Authorization", Tokens.customer(BUYER))
                .header("Idempotency-Key", "checkout-7f3a").contentType(MediaType.APPLICATION_JSON).content(body);

        String txn = body(mvc.perform(request).andExpect(status().isCreated())).get("transactionId").asText();
        mvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.transactionId").value(txn));
        assertThat(payments.count()).isEqualTo(1);

        // the same key can't be reused for another order
        stubProduct(77L, SELLER, 50_000L, "Desk lamp");
        long second = placeOrder(BUYER, 77L).get("id").asLong();
        mvc.perform(post("/api/payments/simulate").header("Authorization", Tokens.customer(BUYER))
                        .header("Idempotency-Key", "checkout-7f3a").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":%d,\"method\":\"UPI\",\"amountPaise\":51000}".formatted(second)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Idempotency-Key")));
    }

    @Test
    void productServiceRefusalRollsBackThePayment() throws Exception {
        JsonNode order = placeOrder(BUYER, PRODUCT);
        long orderId = order.get("id").asLong();
        doThrow(new ProductServiceException(409, "Product 42 is not reserved for order X", null))
                .when(productService).confirmSale(eq(PRODUCT), anyLong(), anyString(), anyString());

        payOrder(BUYER, orderId, "UPI", "SUCCESS", 102_000).andExpect(status().isConflict());

        assertThat(payments.count()).isZero();
        assertThat(commissions.count()).isZero();
        assertThat(payouts.count()).isZero();
        assertThat(ledger.count()).isZero();
        assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void onlyTheBuyerCanPayTheOrder() throws Exception {
        long orderId = placeOrder(BUYER, PRODUCT).get("id").asLong();
        payOrder(SELLER, orderId, "UPI", "SUCCESS", 102_000).andExpect(status().isNotFound());
        payOrder(STRANGER, orderId, "UPI", "SUCCESS", 102_000).andExpect(status().isNotFound());
        payOrder(BUYER, 987_654, "UPI", "SUCCESS", 102_000).andExpect(status().isNotFound());
        assertThat(payments.count()).isZero();
    }

    @Test
    void cardOrBankDetailsAreNeverStoredOrEchoed() throws Exception {
        long orderId = placeOrder(BUYER, PRODUCT).get("id").asLong();
        String response = postJson("/api/payments/simulate", Tokens.customer(BUYER), """
                {"orderId":%d,"method":"CARD","amountPaise":102000,
                 "cardNumber":"4111111111111111","cvv":"123","upiPin":"246813579","accountNumber":"00112233"}
                """.formatted(orderId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("4111111111111111", "cvv", "246813579", "00112233");
        String row = jdbc.queryForList("SELECT * FROM payment_transactions").toString();
        assertThat(row).doesNotContain("4111111111111111").doesNotContain("00112233");
        List<String> columns = jdbc.queryForList(
                "SELECT LOWER(column_name) FROM information_schema.columns WHERE LOWER(table_name) = 'payment_transactions'",
                String.class);
        assertThat(columns).noneMatch(c -> c.contains("card") || c.contains("cvv") || c.contains("pin")
                || c.contains("account_number") || c.contains("ifsc"));
    }
}
