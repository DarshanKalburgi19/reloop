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

import com.reloop.orderpayment.exception.ProductServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** order-service → product-service HTTP client: paths, bodies, internal key and error mapping. */
class HttpProductServiceClientTest {

    private static final String BASE = "http://product.test";
    private static final String SOLD = "{\"productId\":42,\"title\":\"iPhone 12\",\"sellerAccountId\":701,\"buyerAccountId\":501,"
            + "\"pricePaise\":100000,\"status\":\"SOLD\",\"passportCode\":\"RLP-ABCD-2345\",\"reservationReference\":\"ORD-2026-1006\"}";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final HttpProductServiceClient client = new HttpProductServiceClient(builder, BASE, "k3y");

    @Test
    void confirmSaleSendsOrderAndTransactionAndParsesTheSale() {
        server.expect(requestTo(BASE + "/api/internal/products/42/confirm-sale"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Api-Key", "k3y"))
                .andExpect(jsonPath("$.buyerAccountId").value(501))
                .andExpect(jsonPath("$.orderReference").value("ORD-2026-1006"))
                .andExpect(jsonPath("$.transactionId").value("TXN-20261125-ABCDEFGHJK"))
                .andRespond(withSuccess(SOLD, MediaType.APPLICATION_JSON));

        ProductSale sale = client.confirmSale(42L, 501L, "ORD-2026-1006", "TXN-20261125-ABCDEFGHJK");
        assertThat(sale.status()).isEqualTo("SOLD");
        assertThat(sale.pricePaise()).isEqualTo(100_000L);
        assertThat(sale.reservationReference()).isEqualTo("ORD-2026-1006");
        server.verify();
    }

    @Test
    void saleRefundPostsTheRefundCommand() {
        server.expect(requestTo(BASE + "/api/internal/products/42/sale-refund"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Api-Key", "k3y"))
                .andExpect(jsonPath("$.orderReference").value("ORD-2026-1006"))
                .andExpect(jsonPath("$.transactionId").value("TXN-20261125-ABCDEFGHJK"))
                .andExpect(jsonPath("$.refundReference").value("RFD-20261125-ABCDEFGHJK"))
                .andExpect(jsonPath("$.refundAmountPaise").value(25500))
                .andExpect(jsonPath("$.totalRefundedPaise").value(25500))
                .andExpect(jsonPath("$.fullyRefunded").value(false))
                .andExpect(jsonPath("$.reason").value("ITEM_NOT_AS_DESCRIBED"))
                .andRespond(withSuccess(SOLD, MediaType.APPLICATION_JSON));

        client.recordRefund(42L, new ProductSaleRefundCommand("ORD-2026-1006", "TXN-20261125-ABCDEFGHJK",
                "RFD-20261125-ABCDEFGHJK", 25_500L, 25_500L, false, "ITEM_NOT_AS_DESCRIBED"));
        server.verify();
    }

    @Test
    void conflictKeepsProductServicesMessageAndOtherErrorsAreBadGateway() {
        server.expect(requestTo(BASE + "/api/internal/products/42/sale-refund"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":409,\"message\":\"Product 42 was not sold through order ORD-1\"}"));
        ProductSaleRefundCommand cmd = new ProductSaleRefundCommand("ORD-1", "TXN-1", "RFD-1", 1L, 1L, true, null);
        assertThatThrownBy(() -> client.recordRefund(42L, cmd))
                .isInstanceOfSatisfying(ProductServiceException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(409);
                    assertThat(ex.getMessage()).isEqualTo("Product 42 was not sold through order ORD-1");
                });

        server.reset();
        server.expect(requestTo(BASE + "/api/internal/products/42/release")).andRespond(withServerError());
        assertThatThrownBy(() -> client.release(42L, "ORD-1", "expired"))
                .isInstanceOfSatisfying(ProductServiceException.class, ex -> assertThat(ex.getStatus()).isEqualTo(502));
    }

    @Test
    void unreachableIsServiceUnavailableWithAnActionSpecificMessage() {
        HttpProductServiceClient offline = new HttpProductServiceClient("http://localhost:1", "k3y");
        assertThatThrownBy(() -> offline.recordRefund(42L,
                new ProductSaleRefundCommand("ORD-1", "TXN-1", "RFD-1", 1L, 1L, true, null)))
                .isInstanceOfSatisfying(ProductServiceException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(503);
                    assertThat(ex.getMessage()).contains("refund was not issued");
                });
        assertThatThrownBy(() -> offline.reserve(42L, 501L, "ORD-1"))
                .isInstanceOfSatisfying(ProductServiceException.class, ex ->
                        assertThat(ex.getMessage()).contains("nothing was charged"));
    }
}
