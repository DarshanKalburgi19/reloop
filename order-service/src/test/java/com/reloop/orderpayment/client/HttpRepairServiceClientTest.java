package com.reloop.orderpayment.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.reloop.orderpayment.exception.RepairServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The REST contract order-service → repair-service, checked against a mock server. */
class HttpRepairServiceClientTest {

    private static final String BASE = "http://repair.test";
    private static final PaymentConfirmation CONFIRMATION = new PaymentConfirmation(12L, 34L, "PAY-20261125-ABCDEFGHJK", 153_000L);

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final HttpRepairServiceClient client = new HttpRepairServiceClient(builder, BASE, "k3y");

    @Test
    void postsTheConfirmationWithTheInternalKey() {
        server.expect(requestTo(BASE + "/api/internal/repair-requests/7/payment-confirmation"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Api-Key", "k3y"))
                .andExpect(jsonPath("$.quoteId").value(12))
                .andExpect(jsonPath("$.paymentTransactionId").value(34))
                .andExpect(jsonPath("$.transactionId").value("PAY-20261125-ABCDEFGHJK"))
                .andExpect(jsonPath("$.amountPaise").value(153000))
                .andRespond(withSuccess("{\"id\":7,\"status\":\"PAID\"}", MediaType.APPLICATION_JSON));

        client.confirmPayment(7L, CONFIRMATION);
        server.verify();
    }

    @Test
    void conflictKeepsRepairServicesMessage() {
        server.expect(requestTo(BASE + "/api/internal/repair-requests/7/payment-confirmation"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":409,\"message\":\"Request is CANCELLED\"}"));

        assertThatThrownBy(() -> client.confirmPayment(7L, CONFIRMATION))
                .isInstanceOfSatisfying(RepairServiceException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(409);
                    assertThat(ex.getMessage()).isEqualTo("Request is CANCELLED");
                });
    }

    @Test
    void otherErrorsBecomeBadGateway() {
        server.expect(requestTo(BASE + "/api/internal/repair-requests/7/payment-confirmation"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.confirmPayment(7L, CONFIRMATION))
                .isInstanceOfSatisfying(RepairServiceException.class, ex -> assertThat(ex.getStatus()).isEqualTo(502));
    }

    @Test
    void unreachableIsServiceUnavailable() {
        HttpRepairServiceClient offline = new HttpRepairServiceClient("http://localhost:1", "k3y");

        assertThatThrownBy(() -> offline.confirmPayment(7L, CONFIRMATION))
                .isInstanceOfSatisfying(RepairServiceException.class, ex -> assertThat(ex.getStatus()).isEqualTo(503));
    }
}
