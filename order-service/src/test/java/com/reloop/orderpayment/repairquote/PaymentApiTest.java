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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reloop.orderpayment.client.PaymentConfirmation;
import com.reloop.orderpayment.client.RepairServiceClient;
import com.reloop.orderpayment.entity.CommissionRecord;
import com.reloop.orderpayment.entity.PaymentTransaction;
import com.reloop.orderpayment.entity.ProviderPayout;
import com.reloop.orderpayment.entity.RepairQuote;
import com.reloop.orderpayment.enums.PaymentStatus;
import com.reloop.orderpayment.enums.PayoutStatus;
import com.reloop.orderpayment.enums.ProviderType;
import com.reloop.orderpayment.exception.RepairServiceException;
import com.reloop.orderpayment.repository.CommissionRecordRepository;
import com.reloop.orderpayment.repository.PaymentTransactionRepository;
import com.reloop.orderpayment.repository.ProviderPayoutRepository;
import com.reloop.orderpayment.repository.RepairQuoteRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Simulated repair payments: only the owner can pay, only APPROVED quotes, once; the payment copies the
 * quote's frozen amounts and creates separate commission and payout records; repair-service is told (mocked
 * here) and a refusal there rolls everything back.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentApiTest {

    private static final String KEY = RepairQuoteApiTest.KEY;
    private static final long CUSTOMER = RepairQuoteApiTest.CUSTOMER;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private RepairQuoteRepository quotes;

    @Autowired
    private PaymentTransactionRepository payments;

    @Autowired
    private CommissionRecordRepository commissions;

    @Autowired
    private ProviderPayoutRepository payouts;

    @MockitoBean
    private RepairServiceClient repairService;

    @AfterEach
    void clean() {
        payouts.deleteAll();
        commissions.deleteAll();
        payments.deleteAll();
        quotes.deleteAll();
    }

    private long quote(long requestId, long basePaise) throws Exception {
        String body = mvc.perform(post("/api/internal/repair-quotes").header("X-Internal-Api-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(RepairQuoteApiTest.createBody(requestId, basePaise)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private void decide(long quoteId, String action, long requestId) throws Exception {
        mvc.perform(post("/api/internal/repair-quotes/{id}/{action}", quoteId, action).header("X-Internal-Api-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(RepairQuoteApiTest.decisionBody(requestId, CUSTOMER)))
                .andExpect(status().isOk());
    }

    private long approvedQuote(long requestId, long basePaise) throws Exception {
        long id = quote(requestId, basePaise);
        decide(id, "approve", requestId);
        return id;
    }

    private ResultActions pay(String token, long quoteId) throws Exception {
        return pay(token, "{\"repairQuoteId\":" + quoteId + "}");
    }

    private ResultActions pay(String token, String body) throws Exception {
        return mvc.perform(post("/api/payments/simulate").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void approvedQuoteIsPaidOnceWithSeparateCommissionAndPayout() throws Exception {
        long quoteId = approvedQuote(2001, 150_000);

        String body = pay(TestTokens.customer(CUSTOMER), quoteId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.simulated").value(true))
                .andExpect(jsonPath("$.method").value("UPI"))
                .andExpect(jsonPath("$.repairQuoteId").value(quoteId))
                .andExpect(jsonPath("$.repairRequestId").value(2001))
                .andExpect(jsonPath("$.baseAmountPaise").value(150000))
                .andExpect(jsonPath("$.customerFeePaise").value(3000))
                .andExpect(jsonPath("$.totalChargedPaise").value(153000))
                .andExpect(jsonPath("$.repairerCommissionPaise").value(3000))
                .andExpect(jsonPath("$.providerNetPaise").value(147000))
                .andExpect(jsonPath("$.transactionId").value(org.hamcrest.Matchers.startsWith("PAY-")))
                .andExpect(jsonPath("$.commissionTransactionId").value(org.hamcrest.Matchers.startsWith("COM-")))
                .andExpect(jsonPath("$.payoutReference").value(org.hamcrest.Matchers.startsWith("PO-")))
                .andReturn().getResponse().getContentAsString();
        JsonNode view = json.readTree(body);

        PaymentTransaction p = payments.findByTransactionId(view.get("transactionId").asText()).orElseThrow();
        assertThat(p.getProviderType()).isEqualTo(ProviderType.REPAIRER);
        assertThat(p.getProviderId()).isEqualTo(RepairQuoteApiTest.PROFILE);
        assertThat(p.getPurchaseOrderId()).isNull();

        CommissionRecord c = commissions.findByPaymentTransactionId(p.getId()).orElseThrow();
        assertThat(c.getReferenceType()).isEqualTo("REPAIR_QUOTE");
        assertThat(c.getReferenceId()).isEqualTo(quoteId);
        assertThat(c.getTotalCommissionPaise()).isEqualTo(6_000);

        ProviderPayout payout = payouts.findByPaymentTransactionId(p.getId()).orElseThrow();
        assertThat(payout.getGrossAmountPaise()).isEqualTo(150_000);
        assertThat(payout.getCommissionPaise()).isEqualTo(3_000);
        assertThat(payout.getNetAmountPaise()).isEqualTo(147_000);
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PENDING);

        ArgumentCaptor<PaymentConfirmation> sent = ArgumentCaptor.forClass(PaymentConfirmation.class);
        verify(repairService).confirmPayment(eq(2001L), sent.capture());
        assertThat(sent.getValue()).isEqualTo(new PaymentConfirmation(quoteId, p.getId(), p.getTransactionId(), 153_000L));

        pay(TestTokens.customer(CUSTOMER), quoteId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.transactionId").value(p.getTransactionId()));
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

        pay(TestTokens.customer(CUSTOMER), quoteId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerFeeRate").value(0.05))
                .andExpect(jsonPath("$.customerFeePaise").value(5000))
                .andExpect(jsonPath("$.totalChargedPaise").value(105000));
    }

    @Test
    void rejectedQuoteCannotBePaid() throws Exception {
        long quoteId = quote(2003, 80_000);
        decide(quoteId, "reject", 2003);

        pay(TestTokens.customer(CUSTOMER), quoteId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.quoteStatus").value("REJECTED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("rejected quotes can't be paid")));
        assertThat(payments.findByRepairQuoteIdOrderByIdAsc(quoteId)).isEmpty();
        verify(repairService, never()).confirmPayment(anyLong(), any());
    }

    @Test
    void unapprovedOrSupersededQuoteCannotBePaid() throws Exception {
        long first = approvedQuote(2004, 80_000);
        pay(TestTokens.customer(CUSTOMER), quote(2005, 10_000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.quoteStatus").value("SENT"));

        long revised = quote(2004, 70_000); // revision supersedes the approved-but-unpaid quote
        pay(TestTokens.customer(CUSTOMER), first)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.quoteStatus").value("SUPERSEDED"));
        pay(TestTokens.customer(CUSTOMER), revised)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.quoteStatus").value("SENT"));

        decide(revised, "approve", 2004);
        pay(TestTokens.customer(CUSTOMER), revised)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalChargedPaise").value(71400));
    }

    @Test
    void paidQuoteCannotBeRevised() throws Exception {
        long quoteId = approvedQuote(2006, 50_000);
        pay(TestTokens.customer(CUSTOMER), quoteId).andExpect(status().isCreated());

        mvc.perform(post("/api/internal/repair-quotes").header("X-Internal-Api-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(RepairQuoteApiTest.createBody(2006, 60_000)))
                .andExpect(status().isConflict());
    }

    @Test
    void repairServiceRefusalRollsBackThePayment() throws Exception {
        long quoteId = approvedQuote(2007, 50_000);
        doThrow(new RepairServiceException(409, "Request is CANCELLED", null))
                .when(repairService).confirmPayment(eq(2007L), any());

        pay(TestTokens.customer(CUSTOMER), quoteId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Request is CANCELLED"));

        assertThat(payments.findByRepairQuoteIdOrderByIdAsc(quoteId)).isEmpty();
        assertThat(commissions.count()).isZero();
        assertThat(payouts.count()).isZero();
    }

    @Test
    void repairServiceDownIs503AndNothingIsCharged() throws Exception {
        long quoteId = approvedQuote(2008, 50_000);
        doThrow(new RepairServiceException(503, "Repair service is unavailable", null))
                .when(repairService).confirmPayment(eq(2008L), any());

        pay(TestTokens.customer(CUSTOMER), quoteId).andExpect(status().isServiceUnavailable());
        assertThat(payments.findByRepairQuoteIdOrderByIdAsc(quoteId)).isEmpty();
    }

    @Test
    void simulatedFailureIsRecordedAndTheQuoteStaysPayable() throws Exception {
        long quoteId = approvedQuote(2009, 50_000);

        pay(TestTokens.customer(CUSTOMER), "{\"repairQuoteId\":" + quoteId + ",\"method\":\"CARD\",\"outcome\":\"FAILURE\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILURE"))
                .andExpect(jsonPath("$.method").value("CARD"))
                .andExpect(jsonPath("$.failureReason").exists())
                .andExpect(jsonPath("$.commissionTransactionId").doesNotExist());
        verify(repairService, never()).confirmPayment(anyLong(), any());

        pay(TestTokens.customer(CUSTOMER), quoteId).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SUCCESS"));
        List<PaymentTransaction> attempts = payments.findByRepairQuoteIdOrderByIdAsc(quoteId);
        assertThat(attempts).extracting(PaymentTransaction::getStatus)
                .containsExactly(PaymentStatus.FAILURE, PaymentStatus.SUCCESS);
    }

    @Test
    void onlyTheQuotesCustomerCanPay() throws Exception {
        long quoteId = approvedQuote(2010, 50_000);

        pay(TestTokens.customer(CUSTOMER + 1), quoteId).andExpect(status().isNotFound());
        pay(TestTokens.repairer(CUSTOMER), quoteId).andExpect(status().isForbidden());
        mvc.perform(post("/api/payments/simulate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repairQuoteId\":" + quoteId + "}"))
                .andExpect(status().isUnauthorized());
        assertThat(payments.findByRepairQuoteIdOrderByIdAsc(quoteId)).isEmpty();
    }

    @Test
    void invalidPaymentBodyIs400() throws Exception {
        pay(TestTokens.customer(CUSTOMER), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.repairQuoteId").exists());
        pay(TestTokens.customer(CUSTOMER), "{\"repairQuoteId\":1,\"method\":\"BITCOIN\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.method").exists());
    }

    @Test
    void customerCanReadOnlyTheirOwnPayment() throws Exception {
        long quoteId = approvedQuote(2011, 50_000);
        String txn = json.readTree(pay(TestTokens.customer(CUSTOMER), quoteId).andReturn().getResponse()
                .getContentAsString()).get("transactionId").asText();

        mvc.perform(get("/api/payments/{t}", txn).header("Authorization", TestTokens.customer(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repairRequestId").value(2011))
                .andExpect(jsonPath("$.payoutReference").exists());
        mvc.perform(get("/api/payments/{t}", txn).header("Authorization", TestTokens.customer(CUSTOMER + 1)))
                .andExpect(status().isNotFound());
    }
}
