package com.reloop.orderpayment.repairquote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reloop.orderpayment.entity.RepairQuote;
import com.reloop.orderpayment.enums.QuoteStatus;
import com.reloop.orderpayment.repository.RepairQuoteRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Internal quote API used by repair-service: key check, fee freeze, revisions, approval rules. */
@SpringBootTest
@AutoConfigureMockMvc
class RepairQuoteApiTest {

    static final String KEY = "test-internal-key";
    static final long CUSTOMER = 501L;
    static final long PROFILE = 77L;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private RepairQuoteRepository quotes;

    @AfterEach
    void clean() {
        quotes.deleteAll();
    }

    static String createBody(long requestId, long basePaise) {
        return """
                {"repairRequestId":%d,"requestNumber":"RR-20261125-ABCDEFGH","repairerProfileId":%d,
                 "customerAccountId":%d,"baseAmountPaise":%d,"summary":"Replace screen"}
                """.formatted(requestId, PROFILE, CUSTOMER, basePaise);
    }

    static String decisionBody(long requestId, long customerId) {
        return "{\"repairRequestId\":%d,\"customerAccountId\":%d,\"note\":\"ok\"}".formatted(requestId, customerId);
    }

    private ResultActions create(long requestId, long basePaise) throws Exception {
        return mvc.perform(post("/api/internal/repair-quotes").header("X-Internal-Api-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON).content(createBody(requestId, basePaise)));
    }

    private long createId(long requestId, long basePaise) throws Exception {
        JsonNode node = json.readTree(create(requestId, basePaise).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    private ResultActions decide(long quoteId, String action, long requestId, long customerId) throws Exception {
        return mvc.perform(post("/api/internal/repair-quotes/{id}/{action}", quoteId, action)
                .header("X-Internal-Api-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON).content(decisionBody(requestId, customerId)));
    }

    @Test
    void missingOrWrongInternalKeyIs401() throws Exception {
        mvc.perform(post("/api/internal/repair-quotes").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(1, 1000)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/internal/repair-quotes").header("X-Internal-Api-Key", "nope")
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(1, 1000)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Missing or invalid X-Internal-Api-Key"));
        assertThat(quotes.count()).isZero();
    }

    @Test
    void createComputesAndFreezesFeesFromConfiguredRates() throws Exception {
        create(1001, 150_000)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.repairRequestId").value(1001))
                .andExpect(jsonPath("$.baseAmountPaise").value(150000))
                .andExpect(jsonPath("$.customerFeeRate").value(0.02))
                .andExpect(jsonPath("$.customerFeePaise").value(3000))
                .andExpect(jsonPath("$.totalPayablePaise").value(153000))
                .andExpect(jsonPath("$.repairerCommissionRate").value(0.02))
                .andExpect(jsonPath("$.repairerCommissionPaise").value(3000))
                .andExpect(jsonPath("$.repairerNetPaise").value(147000))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.summary").value("Replace screen"));

        RepairQuote q = quotes.findFirstByRepairRequestExternalIdOrderByQuoteRevisionDesc(1001L).orElseThrow();
        assertThat(q.getCustomerFeeRate()).isEqualByComparingTo("0.02");
        assertThat(q.getValidUntil()).isEqualTo(q.getSentAt().plusDays(7));
    }

    @Test
    void invalidBodyIs400WithFieldErrors() throws Exception {
        create(1002, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.baseAmountPaise").exists());
        mvc.perform(post("/api/internal/repair-quotes").header("X-Internal-Api-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"baseAmountPaise\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.repairRequestId").exists())
                .andExpect(jsonPath("$.fieldErrors.customerAccountId").exists());
    }

    @Test
    void revisionSupersedesThePreviousQuoteWhichCanNoLongerBeApproved() throws Exception {
        long first = createId(1003, 100_000);
        create(1003, 120_000)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.totalPayablePaise").value(122400));

        assertThat(quotes.findById(first).orElseThrow().getStatus()).isEqualTo(QuoteStatus.SUPERSEDED);
        decide(first, "approve", 1003, CUSTOMER)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.quoteStatus").value("SUPERSEDED"));
    }

    @Test
    void revisingAnApprovedButUnpaidQuoteRequiresFreshApproval() throws Exception {
        long first = createId(1004, 100_000);
        decide(first, "approve", 1004, CUSTOMER).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));

        long second = createId(1004, 90_000);

        assertThat(quotes.findById(first).orElseThrow().getStatus()).isEqualTo(QuoteStatus.SUPERSEDED);
        assertThat(quotes.findById(second).orElseThrow().getStatus()).isEqualTo(QuoteStatus.SENT);
        assertThat(quotes.findById(second).orElseThrow().getQuoteRevision()).isEqualTo(2);
    }

    @Test
    void approveIsIdempotentAndRejectAfterApproveIs409() throws Exception {
        long id = createId(1005, 50_000);
        decide(id, "approve", 1005, CUSTOMER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedAt").exists());
        decide(id, "approve", 1005, CUSTOMER).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        decide(id, "reject", 1005, CUSTOMER).andExpect(status().isConflict());
    }

    @Test
    void rejectedQuoteCannotBeApprovedLater() throws Exception {
        long id = createId(1006, 50_000);
        decide(id, "reject", 1006, CUSTOMER).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        decide(id, "approve", 1006, CUSTOMER)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.quoteStatus").value("REJECTED"));
    }

    @Test
    void decisionsForAnotherCustomerOrRequestAre404() throws Exception {
        long id = createId(1007, 50_000);
        decide(id, "approve", 1007, 999).andExpect(status().isNotFound());
        decide(id, "approve", 9999, CUSTOMER).andExpect(status().isNotFound());
        decide(424242, "approve", 1007, CUSTOMER).andExpect(status().isNotFound());
    }

    @Test
    void expiredQuoteCannotBeApproved() throws Exception {
        long id = createId(1008, 50_000);
        RepairQuote q = quotes.findById(id).orElseThrow();
        q.setValidUntil(LocalDateTime.now().minusMinutes(1));
        quotes.save(q);

        decide(id, "approve", 1008, CUSTOMER)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("expired")));
    }

    @Test
    void getReturnsTheQuote() throws Exception {
        long id = createId(1009, 50_000);
        mvc.perform(get("/api/internal/repair-quotes/{id}", id).header("X-Internal-Api-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/internal/repair-quotes/{id}", id)).andExpect(status().isUnauthorized());
    }
}
