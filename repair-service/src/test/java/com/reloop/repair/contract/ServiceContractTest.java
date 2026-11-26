package com.reloop.repair.contract;

import static com.reloop.repair.contract.Contracts.assertAccepts;
import static com.reloop.repair.contract.Contracts.assertProduces;
import static com.reloop.repair.contract.Contracts.assertReads;
import static com.reloop.repair.contract.Contracts.assertSends;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.reloop.repair.client.CreateQuoteCommand;
import com.reloop.repair.client.HttpOrderServiceClient;
import com.reloop.repair.client.MarketplaceOrderView;
import com.reloop.repair.client.QuoteDecisionCommand;
import com.reloop.repair.client.QuoteView;
import com.reloop.repair.dto.CreateRepairerProfileRequest;
import com.reloop.repair.dto.RepairerProfileResponse;
import com.reloop.repair.dto.repairrequest.PaymentConfirmationRequest;
import com.reloop.repair.dto.repairrequest.PaymentEventRequest;
import com.reloop.repair.enums.PaymentEventType;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * repair-service's side of the inter-service contracts (fixtures in {@code contracts/}): the payment callbacks and
 * repairer-profile API it provides, and the quote / order API it calls on order-service.
 */
class ServiceContractTest {

    /* ── order-service → repair-service (repair-service is the provider) ── */

    @Test
    void paymentCallbacksAcceptWhatOrderServiceSends() {
        assertAccepts("order-to-repair/payment-confirmation.request.json", PaymentConfirmationRequest.class);
        PaymentEventRequest failed = assertAccepts("order-to-repair/payment-event.failed.request.json", PaymentEventRequest.class);
        assertThat(failed.type()).isEqualTo(PaymentEventType.FAILED);
        PaymentEventRequest refunded = assertAccepts("order-to-repair/payment-event.refunded.request.json", PaymentEventRequest.class);
        assertThat(refunded.type()).isEqualTo(PaymentEventType.REFUNDED);
    }

    /* ── product-service → repair-service (repair-service is the provider) ── */

    @Test
    void repairerProfileApiAcceptsWhatProductServiceSends() {
        assertAccepts("product-to-repair/create-profile.request.json", CreateRepairerProfileRequest.class);
        assertProduces("product-to-repair/repairer-profile.response.json", RepairerProfileResponse.class);
    }

    /* ── repair-service → order-service ── */

    @Test
    void quoteAndOrderMessages() {
        assertSends("repair-to-order/create-quote.request.json", CreateQuoteCommand.class);
        assertSends("repair-to-order/quote-decision.request.json", QuoteDecisionCommand.class);
        QuoteView quote = assertReads("repair-to-order/repair-quote.response.json", QuoteView.class);
        assertThat(quote.totalPayablePaise()).isEqualTo(244_800L);
        MarketplaceOrderView order = assertReads("repair-to-order/order.response.json", MarketplaceOrderView.class);
        assertThat(order.isConfirmed()).isTrue();
    }

    @Test
    void theHttpClientWritesExactlyTheFixtures() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String quote = raw("repair-to-order/repair-quote.response.json");
        server.expect(requestTo("http://o/api/internal/repair-quotes"))
                .andExpect(content().json(raw("repair-to-order/create-quote.request.json"), true))
                .andRespond(withSuccess(quote, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://o/api/internal/repair-quotes/12/approve"))
                .andExpect(content().json(raw("repair-to-order/quote-decision.request.json"), true))
                .andRespond(withSuccess(quote, MediaType.APPLICATION_JSON));
        HttpOrderServiceClient client = new HttpOrderServiceClient(builder, "http://o", "k");
        QuoteView created = client.createQuote(assertSends("repair-to-order/create-quote.request.json", CreateQuoteCommand.class));
        assertThat(created.id()).isEqualTo(12L);
        client.approveQuote(12L, assertSends("repair-to-order/quote-decision.request.json", QuoteDecisionCommand.class));
        server.verify();
    }

    private static String raw(String path) throws Exception {
        return Files.readString(Path.of("..", "contracts", path));
    }
}
