package com.reloop.product.contract;

import static com.reloop.product.contract.Contracts.assertAccepts;
import static com.reloop.product.contract.Contracts.assertProduces;
import static com.reloop.product.contract.Contracts.assertReads;
import static com.reloop.product.contract.Contracts.assertSends;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.reloop.product.client.CreateRepairerProfileCommand;
import com.reloop.product.client.HttpRepairerProfileClient;
import com.reloop.product.client.RepairerProfileView;
import com.reloop.product.dto.sale.ProductReservationRequest;
import com.reloop.product.dto.sale.ProductSaleRefundRequest;
import com.reloop.product.dto.sale.ProductSaleResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * product-service's side of the inter-service contracts (fixtures in {@code contracts/}): the internal sale API it
 * provides to order-service, and the repairer-profile API it calls on repair-service.
 */
class ServiceContractTest {

    /* ── order-service → product-service (product-service is the provider) ── */

    @Test
    void saleApiAcceptsWhatOrderServiceSends() {
        assertAccepts("order-to-product/reserve.request.json", ProductReservationRequest.class);
        assertAccepts("order-to-product/confirm-sale.request.json", ProductReservationRequest.class);
        assertAccepts("order-to-product/release.request.json", ProductReservationRequest.class);
        ProductSaleRefundRequest refund = assertAccepts("order-to-product/sale-refund.request.json", ProductSaleRefundRequest.class);
        assertThat(refund.refundReference()).startsWith("RFD-");
        assertProduces("order-to-product/product-sale.response.json", ProductSaleResponse.class);
    }

    /* ── product-service → repair-service ── */

    @Test
    void repairerProfileRequestAndResponse() {
        assertSends("product-to-repair/create-profile.request.json", CreateRepairerProfileCommand.class);
        RepairerProfileView view = assertReads("product-to-repair/repairer-profile.response.json", RepairerProfileView.class);
        assertThat(view.accountExternalId()).isEqualTo(4L);
    }

    @Test
    void theHttpClientWritesExactlyTheFixture() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://r/api/repairer-profiles"))
                .andExpect(content().json(raw("product-to-repair/create-profile.request.json"), true))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body(raw("product-to-repair/repairer-profile.response.json")));
        HttpRepairerProfileClient client = new HttpRepairerProfileClient(builder, "http://r", "k");
        RepairerProfileView created = client.createProfile(
                assertSends("product-to-repair/create-profile.request.json", CreateRepairerProfileCommand.class));
        assertThat(created.id()).isEqualTo(1L);
        server.verify();
    }

    private static String raw(String path) throws Exception {
        return Files.readString(Path.of("..", "contracts", path));
    }
}
