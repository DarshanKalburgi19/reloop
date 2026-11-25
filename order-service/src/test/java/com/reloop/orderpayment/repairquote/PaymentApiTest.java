package com.reloop.orderpayment.repairquote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.reloop.orderpayment.client.PaymentConfirmation;
import com.reloop.orderpayment.entity.CommissionRecord;
import com.reloop.orderpayment.entity.PaymentTransaction;
import com.reloop.orderpayment.entity.ProviderPayout;
import com.reloop.orderpayment.entity.RepairQuote;
import com.reloop.orderpayment.enums.PaymentStatus;
import com.reloop.orderpayment.enums.PayoutStatus;
import com.reloop.orderpayment.enums.ProviderType;
import com.reloop.orderpayment.enums.TransactionType;
import com.reloop.orderpayment.exception.RepairServiceException;
import com.reloop.orderpayment.support.ApiTestBase;
import com.reloop.orderpayment.support.Tokens;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;

/**
 * Simulated REPAIR_BOOKING payments: only the owner can pay, only APPROVED quotes, once, for the exact amount; the
 * payment copies the quote's frozen amounts and creates separate commission and payout records; repair-service is
 * told (mocked here) and a refusal there rolls everything back.
 */
class PaymentApiTest extends ApiTestBase {

    private org.springframework.test.web.servlet.ResultActions pay(long quoteId) throws Exception {
        return payQuote(BUYER, quoteId, "UPI", "SUCCESS", quoteTotal(quoteId));
    }

    @Test
    void approvedQuoteIsPaidOnceWithSeparateCommissionAndPayout() throws Exception {
        long quoteId = approvedQuote(2001, 150_000);

        JsonNode view = body(pay(quoteId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.transactionType").value("REPAIR_BOOKING"))
                .andExpect(jsonPath("$.simulated").value(true))
                .andExpect(jsonPath("$.method").value("UPI"))
                .andExpect(jsonPath("$.repairQuoteId").value(quoteId))
                .andExpect(jsonPath("$.repairRequestId").value(2001))
                .andExpect(jsonPath("$.providerType").value("REPAIRER"))
                .andExpect(jsonPath("$.providerAccountId").value(REPAIRER_ACCOUNT))
                .andExpect(jsonPath("$.baseAmountPaise").value(150000))
                .andExpect(jsonPath("$.customerFeePaise").value(3000))
                .andExpect(jsonPath("$.totalChargedPaise").value(153000))
                .andExpect(jsonPath("$.providerCommissionPaise").value(3000))
                .andExpect(jsonPath("$.providerNetPaise").value(147000))
                .andExpect(jsonPath("$.platformRevenuePaise").value(6000))
                .andExpect(jsonPath("$.transactionId").value(org.hamcrest.Matchers.matchesPattern("TXN-\\d{8}-[0-9A-Z]{10}")))
                .andExpect(jsonPath("$.commissionTransactionId").value(org.hamcrest.Matchers.startsWith("COM-")))
                .andExpect(jsonPath("$.payoutReference").value(org.hamcrest.Matchers.startsWith("PO-")))
                .andExpect(jsonPath("$.receiptUrl").exists()));

        PaymentTransaction p = payments.findByTransactionId(view.get("transactionId").asText()).orElseThrow();
        assertThat(p.getTransactionType()).isEqualTo(TransactionType.REPAIR_BOOKING);
        assertThat(p.getProviderType()).isEqualTo(ProviderType.REPAIRER);
        assertThat(p.getProviderId()).isEqualTo(REPAIRER_PROFILE);
        assertThat(p.getProviderAccountId()).isEqualTo(REPAIRER_ACCOUNT);
        assertThat(p.getPurchaseOrderId()).isNull();
        assertThat(p.getSettlementKey()).isEqualTo("QUOTE-" + quoteId);

        CommissionRecord c = commissions.findByPaymentTransactionId(p.getId()).orElseThrow();
        assertThat(c.getReferenceType()).isEqualTo("REPAIR_QUOTE");
        assertThat(c.getReferenceId()).isEqualTo(quoteId);
        assertThat(c.getTotalCommissionPaise()).isEqualTo(6_000);

        ProviderPayout payout = payouts.findByPaymentTransactionId(p.getId()).orElseThrow();
        assertThat(payout.getGrossAmountPaise()).isEqualTo(150_000);
        assertThat(payout.getCommissionPaise()).isEqualTo(3_000);
        assertThat(payout.getNetAmountPaise()).isEqualTo(147_000);
        assertThat(payout.getProviderAccountId()).isEqualTo(REPAIRER_ACCOUNT);
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PENDING);

        ArgumentCaptor<PaymentConfirmation> sent = ArgumentCaptor.forClass(PaymentConfirmation.class);
        verify(repairService).confirmPayment(eq(2001L), sent.capture());
        assertThat(sent.getValue()).isEqualTo(new PaymentConfirmation(quoteId, p.getId(), p.getTransactionId(), 153_000L));

        // duplicate: the same quote can't be paid twice
        pay(quoteId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.transactionId").value(p.getTransactionId()));
        assertThat(payments.findByRepairQuoteIdOrderByIdAsc(quoteId)).hasSize(1);
    }

    @Test
    void paymentUsesTheQuotesFrozenRatesNotTodaysConfiguration() throws Exception {
        long quoteId = approvedQuote(2002, 100_000);
        // simulate a quote issued when the platform fee was 5%: the payment must keep those numbers
        RepairQuote q = quotes.findById(quoteId).orElseThrow();
        q.setCustomerFeeRate(new BigDecimal("0.0500"));
        q.setCustomerFeePaise(5_000L);
        q.setTotalPayablePaise(105_000L);
        quotes.save(q);

        payQuote(BUYER, quoteId, "WALLET", "SUCCESS", 105_000)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerFeeRate").value(0.05))
                .andExpect(jsonPath("$.customerFeePaise").value(5000))
                .andExpect(jsonPath("$.totalChargedPaise").value(105000))
                .andExpect(jsonPath("$.platformRevenuePaise").value(7000));
    }

    @Test
    void incorrectAmountIsRejectedAndNothingIsRecorded() throws Exception {
        long quoteId = approvedQuote(2012, 100_000);

        payQuote(BUYER, quoteId, "UPI", "SUCCESS", 100_000)   // forgot the 2% customer fee
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.expectedAmountPaise").value(102000))
                .andExpect(jsonPath("$.receivedAmountPaise").value(100000))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Incorrect amount")));
        assertThat(payments.findByRepairQuoteIdOrderByIdAsc(quoteId)).isEmpty();
        verify(repairService, never()).confirmPayment(anyLong(), any());
    }

    @Test
    void rejectedQuoteCannotBePaid() throws Exception {
        long quoteId = repairQuote(2003, 80_000);
        decideQuote(quoteId, "reject", 2003);

        pay(quoteId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.quoteStatus").value("REJECTED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("rejected quotes can't be paid")));
        assertThat(payments.findByRepairQuoteIdOrderByIdAsc(quoteId)).isEmpty();
        verify(repairService, never()).confirmPayment(anyLong(), any());
    }

    @Test
    void unapprovedOrSupersededQuoteCannotBePaid() throws Exception {
        long first = approvedQuote(2004, 80_000);
        pay(repairQuote(2005, 10_000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.quoteStatus").value("SENT"));

        long revised = repairQuote(2004, 70_000); // revision supersedes the approved-but-unpaid quote
        pay(first).andExpect(status().isConflict()).andExpect(jsonPath("$.quoteStatus").value("SUPERSEDED"));
        pay(revised).andExpect(status().isConflict()).andExpect(jsonPath("$.quoteStatus").value("SENT"));

        decideQuote(revised, "approve", 2004);
        pay(revised).andExpect(status().isCreated()).andExpect(jsonPath("$.totalChargedPaise").value(71400));
        // the revision keeps the repairer's account id from the earlier quote
        assertThat(quotes.findById(revised).orElseThrow().getRepairerAccountId()).isEqualTo(REPAIRER_ACCOUNT);
    }

    @Test
    void paidQuoteCannotBeRevised() throws Exception {
        long quoteId = approvedQuote(2006, 50_000);
        pay(quoteId).andExpect(status().isCreated());

        mvc.perform(post("/api/internal/repair-quotes").header("X-Internal-Api-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(RepairQuoteApiTest.createBody(2006, 60_000)))
                .andExpect(status().isConflict());
    }

    @Test
    void repairServiceRefusalRollsBackThePayment() throws Exception {
        long quoteId = approvedQuote(2007, 50_000);
        doThrow(new RepairServiceException(409, "Request is CANCELLED", null))
                .when(repairService).confirmPayment(eq(2007L), any());

        pay(quoteId).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Request is CANCELLED"));

        assertThat(payments.findByRepairQuoteIdOrderByIdAsc(quoteId)).isEmpty();
        assertThat(commissions.count()).isZero();
        assertThat(payouts.count()).isZero();
        assertThat(ledger.count()).isZero();
    }

    @Test
    void repairServiceDownIs503AndNothingIsCharged() throws Exception {
        long quoteId = approvedQuote(2008, 50_000);
        doThrow(new RepairServiceException(503, "Repair service is unavailable", null))
                .when(repairService).confirmPayment(eq(2008L), any());

        pay(quoteId).andExpect(status().isServiceUnavailable());
        assertThat(payments.findByRepairQuoteIdOrderByIdAsc(quoteId)).isEmpty();
    }

    @Test
    void failedAndCancelledPaymentsAreRecordedAndTheQuoteStaysPayable() throws Exception {
        long quoteId = approvedQuote(2009, 50_000);
        long total = quoteTotal(quoteId);

        payQuote(BUYER, quoteId, "CARD", "FAILED", total)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.method").value("CARD"))
                .andExpect(jsonPath("$.failureReason").exists())
                .andExpect(jsonPath("$.commissionTransactionId").doesNotExist())
                .andExpect(jsonPath("$.payoutReference").doesNotExist())
                .andExpect(jsonPath("$.currentProviderNetPaise").value(0));
        payQuote(BUYER, quoteId, "NET_BANKING", "CANCELLED", total)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        verify(repairService, never()).confirmPayment(anyLong(), any());
        assertThat(commissions.count()).isZero();
        assertThat(payouts.count()).isZero();
        assertThat(ledger.count()).isZero();

        pay(quoteId).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SUCCESS"));
        List<PaymentTransaction> attempts = payments.findByRepairQuoteIdOrderByIdAsc(quoteId);
        assertThat(attempts).extracting(PaymentTransaction::getStatus)
                .containsExactly(PaymentStatus.FAILED, PaymentStatus.CANCELLED, PaymentStatus.SUCCESS);
        assertThat(attempts).extracting(PaymentTransaction::getTransactionId).doesNotHaveDuplicates();
    }

    @Test
    void onlyTheQuotesCustomerCanPay() throws Exception {
        long quoteId = approvedQuote(2010, 50_000);
        long total = quoteTotal(quoteId);

        payQuote(STRANGER, quoteId, "UPI", "SUCCESS", total).andExpect(status().isNotFound());
        postJson("/api/payments/simulate", Tokens.repairer(BUYER),
                "{\"repairQuoteId\":%d,\"method\":\"UPI\",\"amountPaise\":%d}".formatted(quoteId, total))
                .andExpect(status().isForbidden());
        postJson("/api/payments/simulate", null,
                "{\"repairQuoteId\":%d,\"method\":\"UPI\",\"amountPaise\":%d}".formatted(quoteId, total))
                .andExpect(status().isUnauthorized());
        assertThat(payments.findByRepairQuoteIdOrderByIdAsc(quoteId)).isEmpty();
    }

    @Test
    void invalidPaymentBodyIs400() throws Exception {
        String token = Tokens.customer(BUYER);
        postJson("/api/payments/simulate", token, "{\"method\":\"UPI\",\"amountPaise\":100}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.orderId").exists());
        postJson("/api/payments/simulate", token, "{\"orderId\":1,\"repairQuoteId\":1,\"method\":\"UPI\",\"amountPaise\":100}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.orderId").exists());
        postJson("/api/payments/simulate", token, "{\"repairQuoteId\":1,\"method\":\"BITCOIN\",\"amountPaise\":100}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.method").exists());
        postJson("/api/payments/simulate", token, "{\"repairQuoteId\":1,\"amountPaise\":100}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.method").exists());
        postJson("/api/payments/simulate", token, "{\"repairQuoteId\":1,\"method\":\"UPI\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amountPaise").exists());
        postJson("/api/payments/simulate", token, "{\"repairQuoteId\":1,\"method\":\"UPI\",\"amountPaise\":100,\"outcome\":\"MAYBE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.outcome").exists());
    }

    @Test
    void customerCanReadOnlyTheirOwnPayment() throws Exception {
        long quoteId = approvedQuote(2011, 50_000);
        String txn = body(pay(quoteId)).get("transactionId").asText();

        mvc.perform(get("/api/payments/{t}", txn).header("Authorization", Tokens.customer(BUYER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repairRequestId").value(2011))
                .andExpect(jsonPath("$.payoutReference").exists());
        mvc.perform(get("/api/payments/{t}", txn).header("Authorization", Tokens.customer(STRANGER)))
                .andExpect(status().isNotFound());
        // the repairer sees the same transaction through /api/transactions
        mvc.perform(get("/api/transactions/{t}", txn).header("Authorization", Tokens.repairer(REPAIRER_ACCOUNT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerNetPaise").value(49000));
    }
}
