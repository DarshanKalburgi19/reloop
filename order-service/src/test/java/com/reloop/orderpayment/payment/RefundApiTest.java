package com.reloop.orderpayment.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.reloop.orderpayment.entity.CommissionRecord;
import com.reloop.orderpayment.entity.LedgerEntry;
import com.reloop.orderpayment.entity.PaymentTransaction;
import com.reloop.orderpayment.entity.ProviderPayout;
import com.reloop.orderpayment.enums.CommissionStatus;
import com.reloop.orderpayment.enums.LedgerAccount;
import com.reloop.orderpayment.enums.LedgerEntryType;
import com.reloop.orderpayment.enums.OrderStatus;
import com.reloop.orderpayment.enums.PaymentStatus;
import com.reloop.orderpayment.enums.PayoutStatus;
import com.reloop.orderpayment.service.LedgerService;
import com.reloop.orderpayment.support.ApiTestBase;
import com.reloop.orderpayment.support.Tokens;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Simulated refunds: proportional split, status changes and ledger reversal; who may refund. */
class RefundApiTest extends ApiTestBase {

    @Autowired
    private LedgerService ledgerService;

    private ResultActions refund(long as, String txn, String body) throws Exception {
        return postJson("/api/transactions/" + txn + "/refunds", Tokens.customer(as), body);
    }

    @Test
    void partialThenFullRefundReversesFeesCommissionPayoutAndLedger() throws Exception {
        JsonNode paid = paidOrder(BUYER, PRODUCT);                     // ₹1,000 + ₹20 fee
        String txn = paid.get("transactionId").asText();
        long orderId = paid.get("purchaseOrderId").asLong();

        // seller refunds ₹250 of the item price → ₹255 back to the buyer, ₹245 from the seller, ₹10 ReLoop revenue
        refund(SELLER, txn, "{\"baseAmountPaise\":25000,\"reason\":\"ITEM_NOT_AS_DESCRIBED\",\"note\":\"Scratch not shown\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_REFUNDED"))
                .andExpect(jsonPath("$.refundedAmountPaise").value(25500))
                .andExpect(jsonPath("$.reversedProviderNetPaise").value(24500))
                .andExpect(jsonPath("$.reversedPlatformRevenuePaise").value(1000))
                .andExpect(jsonPath("$.currentProviderNetPaise").value(73500))
                .andExpect(jsonPath("$.currentPlatformRevenuePaise").value(3000))
                .andExpect(jsonPath("$.commissionStatus").value("PARTIALLY_REVERSED"))
                .andExpect(jsonPath("$.payoutStatus").value("PARTIALLY_REVERSED"))
                .andExpect(jsonPath("$.refunds[0].refundReference").value(org.hamcrest.Matchers.startsWith("RFD-")))
                .andExpect(jsonPath("$.refunds[0].refundAmountPaise").value(25500))
                .andExpect(jsonPath("$.refunds[0].refundedBasePaise").value(25000))
                .andExpect(jsonPath("$.refunds[0].refundedCustomerFeePaise").value(500))
                .andExpect(jsonPath("$.refunds[0].reversedProviderCommissionPaise").value(500))
                .andExpect(jsonPath("$.refunds[0].providerNetReversedPaise").value(24500))
                .andExpect(jsonPath("$.refunds[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.refunds[0].reason").value("ITEM_NOT_AS_DESCRIBED"));
        assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PARTIALLY_REFUNDED);

        // full refund of what remains (no amount) → everything nets to zero
        refund(SELLER, txn, "{\"reason\":\"CUSTOMER_REQUEST\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.refundedAmountPaise").value(102000))
                .andExpect(jsonPath("$.currentProviderNetPaise").value(0))
                .andExpect(jsonPath("$.currentPlatformRevenuePaise").value(0))
                .andExpect(jsonPath("$.refunds[1].refundAmountPaise").value(76500));

        PaymentTransaction p = payments.findByTransactionId(txn).orElseThrow();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        CommissionRecord c = commissions.findByPaymentTransactionId(p.getId()).orElseThrow();
        assertThat(c.getStatus()).isEqualTo(CommissionStatus.REVERSED);
        assertThat(c.currentRevenuePaise()).isZero();
        ProviderPayout payout = payouts.findByPaymentTransactionId(p.getId()).orElseThrow();
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.REVERSED);
        assertThat(payout.currentNetPaise()).isZero();
        assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.REFUNDED);

        // ledger: 4 payment lines + 4 lines per refund, each linked to the line it reverses, all accounts back to 0
        List<LedgerEntry> lines = ledger.findByPaymentTransactionIdOrderByIdAsc(p.getId());
        assertThat(lines).hasSize(12);
        List<LedgerEntry> reversals = lines.stream().filter(e -> e.getEntryType() == LedgerEntryType.REFUND).toList();
        assertThat(reversals).allSatisfy(e -> {
            assertThat(e.getReversalOfEntryId()).isNotNull();
            LedgerEntry original = ledger.findById(e.getReversalOfEntryId()).orElseThrow();
            assertThat(original.getAccount()).isEqualTo(e.getAccount());
            assertThat(original.getDirection()).isEqualTo(e.getDirection().opposite());
        });
        Map<LedgerAccount, Long> balances = ledgerService.balances(p.getId());
        assertThat(balances.values()).allMatch(v -> v == 0L);

        // nothing left to refund
        refund(SELLER, txn, "{\"reason\":\"OTHER\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already been fully refunded")));
        mvc.perform(get("/api/transactions/{t}/ledger", txn).header("Authorization", Tokens.customer(BUYER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[4].refundReference").value(org.hamcrest.Matchers.startsWith("RFD-")));
    }

    @Test
    void refundCannotExceedTheRefundableAmount() throws Exception {
        String txn = paidOrder(BUYER, PRODUCT).get("transactionId").asText();
        refund(SELLER, txn, "{\"baseAmountPaise\":100001,\"reason\":\"OTHER\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.baseAmountPaise").exists());
        refund(SELLER, txn, "{\"baseAmountPaise\":0,\"reason\":\"OTHER\"}").andExpect(status().isBadRequest());
        refund(SELLER, txn, "{\"baseAmountPaise\":1000}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").exists());
        assertThat(refunds.count()).isZero();
    }

    @Test
    void overlongIdempotencyKeyIsRejectedBeforeAnythingIsRefunded() throws Exception {
        String txn = paidOrder(BUYER, PRODUCT).get("transactionId").asText();
        mvc.perform(post("/api/transactions/{t}/refunds", txn).header("Authorization", Tokens.customer(SELLER))
                        .header("Idempotency-Key", "k".repeat(101))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"OTHER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['Idempotency-Key']").exists());
        assertThat(refunds.count()).isZero();
    }

    @Test
    void onlyTheProviderOrSupportCanRefund() throws Exception {
        String txn = paidOrder(BUYER, PRODUCT).get("transactionId").asText();
        String body = "{\"reason\":\"CUSTOMER_REQUEST\"}";

        refund(BUYER, txn, body).andExpect(status().isForbidden());
        refund(STRANGER, txn, body).andExpect(status().isNotFound());
        postJson("/api/transactions/" + txn + "/refunds", null, body).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/internal/transactions/{t}/refunds", txn).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/internal/transactions/{t}/refunds", txn).header("X-Internal-Api-Key", "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        assertThat(refunds.count()).isZero();

        // ReLoop support (internal key) handles disputes
        mvc.perform(post("/api/internal/transactions/{t}/refunds", txn).header("X-Internal-Api-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"DISPUTED_ORDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        assertThat(refunds.findAll()).singleElement().satisfies(r -> assertThat(r.getInitiatedByAccountId()).isNull());
    }

    @Test
    void repairerRefundsARepairPayment() throws Exception {
        long quoteId = approvedQuote(3001, 150_000);
        String txn = body(payQuote(BUYER, quoteId, "UPI", "SUCCESS", 153_000)).get("transactionId").asText();

        postJson("/api/transactions/" + txn + "/refunds", Tokens.repairer(REPAIRER_ACCOUNT),
                "{\"baseAmountPaise\":50000,\"reason\":\"REPAIR_NOT_COMPLETED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refunds[0].refundAmountPaise").value(51000))
                .andExpect(jsonPath("$.refunds[0].providerNetReversedPaise").value(49000))
                .andExpect(jsonPath("$.currentProviderNetPaise").value(98000));
    }

    @Test
    void failedOrCancelledPaymentsCannotBeRefunded() throws Exception {
        long orderId = placeOrder(BUYER, PRODUCT).get("id").asLong();
        String failed = body(payOrder(BUYER, orderId, "CARD", "FAILED", 102_000)).get("transactionId").asText();

        refund(SELLER, failed, "{\"reason\":\"OTHER\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.paymentStatus").value("FAILED"));
        assertThat(refunds.count()).isZero();
        assertThat(ledger.count()).isZero();
    }
}
