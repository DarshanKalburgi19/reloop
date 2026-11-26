package com.reloop.orderpayment.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.reloop.orderpayment.client.ProductSaleRefundCommand;
import com.reloop.orderpayment.client.RepairPaymentEvent;
import com.reloop.orderpayment.entity.PaymentTransaction;
import com.reloop.orderpayment.enums.OrderStatus;
import com.reloop.orderpayment.enums.PaymentStatus;
import com.reloop.orderpayment.exception.ProductServiceException;
import com.reloop.orderpayment.exception.RepairServiceException;
import com.reloop.orderpayment.support.ApiTestBase;
import com.reloop.orderpayment.support.Tokens;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Integration rules between order-service and the other two services (Prompt 13): failed repair payments and refunds
 * are reported to repair-service / product-service with deterministic, idempotent ids; a refund that can't be
 * propagated is rolled back completely; a repeated refund request with the same Idempotency-Key refunds once.
 */
class CrossServicePropagationApiTest extends ApiTestBase {

    private ResultActions refund(long as, String txn, String key, String body) throws Exception {
        var request = post("/api/transactions/" + txn + "/refunds").header("Authorization", Tokens.customer(as))
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mvc.perform(request);
    }

    /* ───────────────────────────── failed repair payments ───────────────────────────── */

    @Test
    void failedRepairPaymentIsReportedToRepairServiceWithoutConfirmingIt() throws Exception {
        long quoteId = approvedQuote(4101, 100_000);
        String txn = body(payQuote(BUYER, quoteId, "CARD", "FAILED", 102_000).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))).get("transactionId").asText();

        ArgumentCaptor<RepairPaymentEvent> event = ArgumentCaptor.forClass(RepairPaymentEvent.class);
        verify(repairService).reportPaymentEvent(eq(4101L), event.capture());
        assertThat(event.getValue().eventId()).isEqualTo(txn + ":FAILED");
        assertThat(event.getValue().type()).isEqualTo("FAILED");
        assertThat(event.getValue().quoteId()).isEqualTo(quoteId);
        assertThat(event.getValue().transactionId()).isEqualTo(txn);
        assertThat(event.getValue().amountPaise()).isEqualTo(102_000L);
        assertThat(event.getValue().refundedAmountPaise()).isZero();
        assertThat(event.getValue().reason()).contains("declined");
        verify(repairService, never()).confirmPayment(anyLong(), any());
        // no commission, no payout for a failed attempt
        assertThat(commissions.count()).isZero();
        assertThat(payouts.count()).isZero();
    }

    @Test
    void cancelledRepairPaymentIsReportedAsCancelled() throws Exception {
        long quoteId = approvedQuote(4102, 100_000);
        String txn = body(payQuote(BUYER, quoteId, "UPI", "CANCELLED", 102_000)).get("transactionId").asText();

        ArgumentCaptor<RepairPaymentEvent> event = ArgumentCaptor.forClass(RepairPaymentEvent.class);
        verify(repairService).reportPaymentEvent(eq(4102L), event.capture());
        assertThat(event.getValue().eventId()).isEqualTo(txn + ":CANCELLED");
        assertThat(event.getValue().type()).isEqualTo("CANCELLED");
    }

    @Test
    void failedAttemptIsKeptWhenRepairServiceIsDown() throws Exception {
        long quoteId = approvedQuote(4103, 100_000);
        doThrow(new RepairServiceException(503, "down", null)).when(repairService).reportPaymentEvent(anyLong(), any());

        payQuote(BUYER, quoteId, "CARD", "FAILED", 102_000).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));
        assertThat(payments.findByRepairQuoteIdOrderByIdAsc(quoteId)).singleElement()
                .satisfies(p -> assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED));

        // the customer can still pay afterwards
        doNothing().when(repairService).reportPaymentEvent(anyLong(), any());
        payQuote(BUYER, quoteId, "CARD", "SUCCESS", 102_000).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    /* ───────────────────────────── refund propagation ───────────────────────────── */

    @Test
    void marketplaceRefundsAreRecordedOnTheProduct() throws Exception {
        JsonNode paid = paidOrder(BUYER, PRODUCT);
        String txn = paid.get("transactionId").asText();
        String orderNumber = orders.findById(paid.get("purchaseOrderId").asLong()).orElseThrow().getOrderNumber();

        refund(SELLER, txn, null, "{\"baseAmountPaise\":25000,\"reason\":\"ITEM_NOT_AS_DESCRIBED\"}").andExpect(status().isOk());
        refund(SELLER, txn, null, "{\"reason\":\"CUSTOMER_REQUEST\"}").andExpect(status().isOk());

        ArgumentCaptor<ProductSaleRefundCommand> cmd = ArgumentCaptor.forClass(ProductSaleRefundCommand.class);
        verify(productService, times(2)).recordRefund(eq(PRODUCT), cmd.capture());
        ProductSaleRefundCommand partial = cmd.getAllValues().get(0);
        assertThat(partial.orderReference()).isEqualTo(orderNumber);
        assertThat(partial.transactionId()).isEqualTo(txn);
        assertThat(partial.refundReference()).startsWith("RFD-");
        assertThat(partial.refundAmountPaise()).isEqualTo(25_500L);
        assertThat(partial.totalRefundedPaise()).isEqualTo(25_500L);
        assertThat(partial.fullyRefunded()).isFalse();
        assertThat(partial.reason()).isEqualTo("ITEM_NOT_AS_DESCRIBED");
        ProductSaleRefundCommand full = cmd.getAllValues().get(1);
        assertThat(full.refundAmountPaise()).isEqualTo(76_500L);
        assertThat(full.totalRefundedPaise()).isEqualTo(102_000L);
        assertThat(full.fullyRefunded()).isTrue();
        assertThat(full.refundReference()).isNotEqualTo(partial.refundReference());
    }

    @Test
    void repairRefundsAreReportedWithDeterministicEventIds() throws Exception {
        long quoteId = approvedQuote(4104, 150_000);
        String txn = body(payQuote(BUYER, quoteId, "UPI", "SUCCESS", 153_000)).get("transactionId").asText();

        refund(REPAIRER_ACCOUNT, txn, null, "{\"baseAmountPaise\":50000,\"reason\":\"REPAIR_NOT_COMPLETED\"}")
                .andExpect(status().isOk());
        refund(REPAIRER_ACCOUNT, txn, null, "{\"reason\":\"REPAIR_NOT_COMPLETED\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        ArgumentCaptor<RepairPaymentEvent> event = ArgumentCaptor.forClass(RepairPaymentEvent.class);
        verify(repairService, times(2)).reportPaymentEvent(eq(4104L), event.capture());
        RepairPaymentEvent partial = event.getAllValues().get(0);
        assertThat(partial.type()).isEqualTo("PARTIALLY_REFUNDED");
        assertThat(partial.eventId()).isEqualTo(txn + ":REFUND:51000");
        assertThat(partial.refundedAmountPaise()).isEqualTo(51_000L);
        assertThat(partial.amountPaise()).isEqualTo(153_000L);
        assertThat(partial.quoteId()).isEqualTo(quoteId);
        RepairPaymentEvent full = event.getAllValues().get(1);
        assertThat(full.type()).isEqualTo("REFUNDED");
        assertThat(full.eventId()).isEqualTo(txn + ":REFUND:153000");
        assertThat(full.refundedAmountPaise()).isEqualTo(153_000L);
    }

    @Test
    void refundThatCannotBePropagatedIsRolledBackAndCanBeRetried() throws Exception {
        JsonNode paid = paidOrder(BUYER, PRODUCT);
        String txn = paid.get("transactionId").asText();
        long orderId = paid.get("purchaseOrderId").asLong();
        when(productService.recordRefund(anyLong(), any()))
                .thenThrow(new ProductServiceException(503, "Product service is unavailable; the refund was not issued. Try again shortly", null))
                .thenReturn(null);

        refund(SELLER, txn, "rf-1", "{\"reason\":\"CUSTOMER_REQUEST\"}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("refund was not issued")));
        PaymentTransaction p = payments.findByTransactionId(txn).orElseThrow();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(p.getRefundedAmountPaise()).isZero();
        assertThat(refunds.count()).isZero();
        assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        // same key again once product-service is back: refunded exactly once
        refund(SELLER, txn, "rf-1", "{\"reason\":\"CUSTOMER_REQUEST\"}").andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "false"))
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        assertThat(refunds.count()).isEqualTo(1);
    }

    @Test
    void repairServiceConflictAbortsTheRepairRefund() throws Exception {
        long quoteId = approvedQuote(4105, 100_000);
        String txn = body(payQuote(BUYER, quoteId, "UPI", "SUCCESS", 102_000)).get("transactionId").asText();
        doThrow(new RepairServiceException(409, "Refund is for payment X but this repair request was paid with Y", null))
                .when(repairService).reportPaymentEvent(anyLong(), any());

        refund(REPAIRER_ACCOUNT, txn, null, "{\"reason\":\"OTHER\"}").andExpect(status().isConflict());
        assertThat(refunds.count()).isZero();
        assertThat(payments.findByTransactionId(txn).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    /* ───────────────────────────── duplicate refund requests ───────────────────────────── */

    @Test
    void sameIdempotencyKeyRefundsOnlyOnce() throws Exception {
        String txn = paidOrder(BUYER, PRODUCT).get("transactionId").asText();
        String body = "{\"baseAmountPaise\":10000,\"reason\":\"CUSTOMER_REQUEST\"}";

        refund(SELLER, txn, "double-click-1", body).andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "false"))
                .andExpect(jsonPath("$.refundedAmountPaise").value(10200));
        refund(SELLER, txn, "double-click-1", body).andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.refundedAmountPaise").value(10200))
                .andExpect(jsonPath("$.refunds.length()").value(1));
        assertThat(refunds.count()).isEqualTo(1);
        verify(productService, times(1)).recordRefund(anyLong(), any());

        // a different key is a new refund
        refund(SELLER, txn, "double-click-2", body).andExpect(status().isOk())
                .andExpect(jsonPath("$.refundedAmountPaise").value(20400));
        assertThat(refunds.count()).isEqualTo(2);
    }

    @Test
    void internalRefundsAcceptAnIdempotencyKeyToo() throws Exception {
        String txn = paidOrder(BUYER, PRODUCT).get("transactionId").asText();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/internal/transactions/{t}/refunds", txn).header("X-Internal-Api-Key", KEY)
                            .header("Idempotency-Key", "support-ticket-77")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"DISPUTED_ORDER\"}"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Idempotent-Replay", String.valueOf(i == 1)))
                    .andExpect(jsonPath("$.status").value("REFUNDED"));
        }
        assertThat(refunds.count()).isEqualTo(1);
    }
}
