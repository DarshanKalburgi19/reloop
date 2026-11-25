package com.reloop.repair.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.reloop.repair.exception.OrderServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The REST contract repair-service → order-service, checked against a mock server. */
class HttpOrderServiceClientTest {

    private static final String BASE = "http://order.test";

    /** Shape of order-service's RepairQuoteView, including fields repair-service ignores. */
    private static final String QUOTE_JSON = """
            {"id":12,"repairRequestId":7,"repairerProfileId":3,"customerAccountId":44,"revision":2,"status":"%s",
             "baseAmountPaise":150000,"customerFeeRate":0.0200,"customerFeePaise":3000,"totalPayablePaise":153000,
             "repairerCommissionRate":0.0200,"repairerCommissionPaise":3000,"repairerNetPaise":147000,"currency":"INR",
             "summary":"Screen","sentAt":"2026-11-25T10:15:30","validUntil":"2026-12-02T10:15:30","approvedAt":null,
             "rejectedAt":null}
            """;

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final HttpOrderServiceClient client = new HttpOrderServiceClient(builder, BASE, "k3y");

    @Test
    void createQuotePostsTheCommandWithTheInternalKey() {
        server.expect(requestTo(BASE + "/api/internal/repair-quotes"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Api-Key", "k3y"))
                .andExpect(jsonPath("$.repairRequestId").value(7))
                .andExpect(jsonPath("$.requestNumber").value("RR-20261125-ABCDEFGH"))
                .andExpect(jsonPath("$.baseAmountPaise").value(150000))
                .andExpect(jsonPath("$.repairerAccountId").value(55))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body(QUOTE_JSON.formatted("SENT")));

        QuoteView q = client.createQuote(new CreateQuoteCommand(7L, "RR-20261125-ABCDEFGH", 3L, 44L, 150_000L, "Screen", 55L));

        assertThat(q.id()).isEqualTo(12L);
        assertThat(q.revision()).isEqualTo(2);
        assertThat(q.totalPayablePaise()).isEqualTo(153_000L);
        assertThat(q.customerFeeRate()).isEqualByComparingTo("0.02");
        assertThat(q.sentAt()).isNotNull();
        server.verify();
    }

    @Test
    void approveAndRejectUseTheQuoteId() {
        server.expect(requestTo(BASE + "/api/internal/repair-quotes/12/approve"))
                .andExpect(jsonPath("$.customerAccountId").value(44))
                .andRespond(withSuccess(QUOTE_JSON.formatted("APPROVED"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/api/internal/repair-quotes/12/reject"))
                .andRespond(withSuccess(QUOTE_JSON.formatted("REJECTED"), MediaType.APPLICATION_JSON));

        assertThat(client.approveQuote(12L, new QuoteDecisionCommand(7L, 44L, null)).status()).isEqualTo("APPROVED");
        assertThat(client.rejectQuote(12L, new QuoteDecisionCommand(7L, 44L, "no")).status()).isEqualTo("REJECTED");
        server.verify();
    }

    @Test
    void conflictKeepsOrderServicesMessage() {
        server.expect(requestTo(BASE + "/api/internal/repair-quotes/12/approve"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":409,\"message\":\"Quote #1 can't be approved: it was replaced by a revised quote\"}"));

        assertThatThrownBy(() -> client.approveQuote(12L, new QuoteDecisionCommand(7L, 44L, null)))
                .isInstanceOfSatisfying(OrderServiceException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(409);
                    assertThat(ex.getMessage()).contains("replaced by a revised quote");
                });
    }

    @Test
    void serverErrorsBecomeBadGateway() {
        server.expect(requestTo(BASE + "/api/internal/repair-quotes")).andRespond(withServerError());

        assertThatThrownBy(() -> client.createQuote(new CreateQuoteCommand(7L, "RR", 3L, 44L, 100L, "x", 55L)))
                .isInstanceOfSatisfying(OrderServiceException.class, ex -> assertThat(ex.getStatus()).isEqualTo(502));
    }

    @Test
    void unreachableIsServiceUnavailable() {
        HttpOrderServiceClient offline = new HttpOrderServiceClient("http://localhost:1", "k3y");

        assertThatThrownBy(() -> offline.createQuote(new CreateQuoteCommand(7L, "RR", 3L, 44L, 100L, "x", 55L)))
                .isInstanceOfSatisfying(OrderServiceException.class, ex -> assertThat(ex.getStatus()).isEqualTo(503));
    }
}
