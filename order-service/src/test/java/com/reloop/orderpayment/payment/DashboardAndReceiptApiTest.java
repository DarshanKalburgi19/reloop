package com.reloop.orderpayment.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.reloop.orderpayment.support.ApiTestBase;
import com.reloop.orderpayment.support.Tokens;
import org.junit.jupiter.api.Test;

/**
 * Customer, seller and repairer dashboards read the same records and therefore agree; transactions, payouts and
 * receipts are only visible to the people involved.
 */
class DashboardAndReceiptApiTest extends ApiTestBase {

    private JsonNode getJson(String url, String token) throws Exception {
        return body(mvc.perform(get(url).header("Authorization", token)).andExpect(status().isOk()));
    }

    @Test
    void customerSellerAndRepairerSeeConsistentNumbers() throws Exception {
        // one marketplace purchase (₹1,000), one failed attempt, one repair booking (₹1,500)
        JsonNode purchase = paidOrder(BUYER, PRODUCT);
        stubProduct(43L, SELLER, 20_000L, "Bookshelf");
        long unpaid = placeOrder(BUYER, 43L).get("id").asLong();
        payOrder(BUYER, unpaid, "CARD", "FAILED", 20_400);
        long quoteId = approvedQuote(4001, 150_000);
        JsonNode repair = body(payQuote(BUYER, quoteId, "UPI", "SUCCESS", 153_000));

        JsonNode customer = getJson("/api/customers/" + BUYER + "/payments", Tokens.customer(BUYER));
        assertThat(customer.get("count").asInt()).isEqualTo(3);
        assertThat(customer.get("successfulCount").asInt()).isEqualTo(2);
        assertThat(customer.get("failedCount").asInt()).isEqualTo(1);
        assertThat(customer.get("totalPaidPaise").asLong()).isEqualTo(102_000 + 153_000);
        assertThat(customer.get("totalFeesPaise").asLong()).isEqualTo(2_000 + 3_000);
        assertThat(customer.get("netSpentPaise").asLong()).isEqualTo(255_000);
        assertThat(customer.get("payments").get(0).get("transactionId").asText()).isEqualTo(repair.get("transactionId").asText());

        JsonNode seller = getJson("/api/providers/" + SELLER + "/earnings", Tokens.customer(SELLER));
        assertThat(seller.get("totals").get("transactionCount").asLong()).isEqualTo(1);   // failed attempt: no payout
        assertThat(seller.get("totals").get("grossPaise").asLong()).isEqualTo(100_000);
        assertThat(seller.get("totals").get("commissionPaise").asLong()).isEqualTo(2_000);
        assertThat(seller.get("totals").get("netPaise").asLong()).isEqualTo(98_000);
        assertThat(seller.get("totals").get("pendingPayoutPaise").asLong()).isEqualTo(98_000);
        assertThat(seller.get("byTransactionType").get("MARKETPLACE_PURCHASE").get("netPaise").asLong()).isEqualTo(98_000);
        assertThat(seller.get("byTransactionType").get("REPAIR_BOOKING").get("transactionCount").asLong()).isZero();
        assertThat(seller.get("transactions").get(0).get("transactionId").asText())
                .isEqualTo(purchase.get("transactionId").asText());

        JsonNode repairer = getJson("/api/providers/" + REPAIRER_ACCOUNT + "/earnings", Tokens.repairer(REPAIRER_ACCOUNT));
        assertThat(repairer.get("totals").get("grossPaise").asLong()).isEqualTo(150_000);
        assertThat(repairer.get("totals").get("netPaise").asLong()).isEqualTo(147_000);
        assertThat(repairer.get("byTransactionType").get("REPAIR_BOOKING").get("commissionPaise").asLong()).isEqualTo(3_000);

        // the same transaction seen by buyer and seller is identical
        String txn = purchase.get("transactionId").asText();
        JsonNode byBuyer = getJson("/api/transactions/" + txn, Tokens.customer(BUYER));
        JsonNode bySeller = getJson("/api/transactions/" + txn, Tokens.customer(SELLER));
        assertThat(byBuyer).isEqualTo(bySeller);
        assertThat(byBuyer.get("providerNetPaise").asLong()).isEqualTo(seller.get("totals").get("netPaise").asLong());

        mvc.perform(get("/api/providers/{id}/payouts", SELLER).header("Authorization", Tokens.customer(SELLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.pendingPayoutPaise").value(98000))
                .andExpect(jsonPath("$.payouts[0].transactionId").value(txn))
                .andExpect(jsonPath("$.payouts[0].grossAmountPaise").value(100000))
                .andExpect(jsonPath("$.payouts[0].commissionPaise").value(2000))
                .andExpect(jsonPath("$.payouts[0].netAmountPaise").value(98000))
                .andExpect(jsonPath("$.payouts[0].status").value("PENDING"));
    }

    @Test
    void earningsReflectRefunds() throws Exception {
        String txn = paidOrder(BUYER, PRODUCT).get("transactionId").asText();
        postJson("/api/transactions/" + txn + "/refunds", Tokens.customer(SELLER),
                "{\"baseAmountPaise\":50000,\"reason\":\"CUSTOMER_REQUEST\"}").andExpect(status().isOk());

        JsonNode totals = getJson("/api/providers/" + SELLER + "/earnings", Tokens.customer(SELLER)).get("totals");
        assertThat(totals.get("netPaise").asLong()).isEqualTo(98_000);
        assertThat(totals.get("refundedGrossPaise").asLong()).isEqualTo(50_000);
        assertThat(totals.get("reversedCommissionPaise").asLong()).isEqualTo(1_000);
        assertThat(totals.get("reversedNetPaise").asLong()).isEqualTo(49_000);
        assertThat(totals.get("currentNetPaise").asLong()).isEqualTo(49_000);

        JsonNode customer = getJson("/api/customers/" + BUYER + "/payments", Tokens.customer(BUYER));
        assertThat(customer.get("totalRefundedPaise").asLong()).isEqualTo(51_000);
        assertThat(customer.get("netSpentPaise").asLong()).isEqualTo(51_000);
    }

    @Test
    void dashboardsAndTransactionsAreProtected() throws Exception {
        String txn = paidOrder(BUYER, PRODUCT).get("transactionId").asText();

        mvc.perform(get("/api/customers/{id}/payments", BUYER).header("Authorization", Tokens.customer(SELLER)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/providers/{id}/earnings", SELLER).header("Authorization", Tokens.customer(BUYER)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/providers/{id}/payouts", SELLER).header("Authorization", Tokens.repairer(REPAIRER_ACCOUNT)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/providers/{id}/payouts", SELLER)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/customers/{id}/payments", BUYER)).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/transactions/{t}", txn).header("Authorization", Tokens.customer(STRANGER)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/transactions/{t}", txn)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/transactions/{t}/receipt", txn).header("Authorization", Tokens.customer(STRANGER)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/transactions/{t}/ledger", txn).header("Authorization", Tokens.customer(STRANGER)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/transactions/{t}", "TXN-20261125-NOPENOPENO").header("Authorization", Tokens.customer(BUYER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void printableAndDownloadableReceipts() throws Exception {
        JsonNode t = paidOrder(BUYER, PRODUCT);
        String txn = t.get("transactionId").asText();

        String html = mvc.perform(get("/api/transactions/{t}/receipt", txn).header("Authorization", Tokens.customer(BUYER)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains(txn, t.get("orderNumber").asText(), "Used Sony headphones", "₹1,000.00", "₹20.00",
                "₹1,020.00", "₹980.00", "₹40.00", "SIMULATED", "window.print()");

        String text = mvc.perform(get("/api/transactions/{t}/receipt?format=text&download=true", txn)
                        .header("Authorization", Tokens.customer(SELLER)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.startsWith("attachment"),
                                org.hamcrest.Matchers.containsString("reloop-receipt-" + txn + ".txt"))))
                .andReturn().getResponse().getContentAsString();
        assertThat(text).contains("Payment receipt (simulated)", "Total paid", "₹1,020.00", "Net earnings", "₹980.00",
                "ReLoop revenue", "₹40.00", "no real money");
    }

    @Test
    void receiptEscapesHtmlAndLabelsFailedAttempts() throws Exception {
        stubProduct(48L, SELLER, 10_000L, "<script>alert(1)</script> lamp");
        long orderId = placeOrder(BUYER, 48L).get("id").asLong();
        String txn = body(payOrder(BUYER, orderId, "CARD", "FAILED", 10_200)).get("transactionId").asText();

        String html = mvc.perform(get("/api/transactions/{t}/receipt", txn).header("Authorization", Tokens.customer(BUYER)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("<script>alert(1)</script>").contains("&lt;script&gt;");
        assertThat(html).contains("FAILED", "Total (not charged)");
    }
}
