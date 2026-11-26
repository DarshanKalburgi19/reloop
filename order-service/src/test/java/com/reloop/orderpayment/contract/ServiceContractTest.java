package com.reloop.orderpayment.contract;

import static com.reloop.orderpayment.contract.Contracts.assertAccepts;
import static com.reloop.orderpayment.contract.Contracts.assertProduces;
import static com.reloop.orderpayment.contract.Contracts.assertReads;
import static com.reloop.orderpayment.contract.Contracts.assertSends;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.reloop.orderpayment.client.HttpProductServiceClient;
import com.reloop.orderpayment.client.HttpRepairServiceClient;
import com.reloop.orderpayment.client.PaymentConfirmation;
import com.reloop.orderpayment.client.ProductReservationCommand;
import com.reloop.orderpayment.client.ProductSale;
import com.reloop.orderpayment.client.ProductSaleRefundCommand;
import com.reloop.orderpayment.client.RepairPaymentEvent;
import com.reloop.orderpayment.dto.order.InternalOrderView;
import com.reloop.orderpayment.dto.quote.CreateRepairQuoteRequest;
import com.reloop.orderpayment.dto.quote.QuoteDecisionRequest;
import com.reloop.orderpayment.dto.quote.RepairQuoteView;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * order-service's side of every inter-service contract (fixtures in {@code contracts/}): what it sends to
 * product-service and repair-service, what it reads back, and what it accepts from / returns to repair-service.
 */
class ServiceContractTest {

    /* ── order-service → product-service ── */

    @Test
    void productSaleRequestsAndResponse() {
        assertSends("order-to-product/reserve.request.json", ProductReservationCommand.class);
        assertSends("order-to-product/confirm-sale.request.json", ProductReservationCommand.class);
        assertSends("order-to-product/release.request.json", ProductReservationCommand.class);
        assertSends("order-to-product/sale-refund.request.json", ProductSaleRefundCommand.class);
        ProductSale sale = assertReads("order-to-product/product-sale.response.json", ProductSale.class);
        assertThat(sale.pricePaise()).isEqualTo(100_000L);
    }

    @Test
    void theHttpClientWritesExactlyTheFixtures() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String sold = raw("order-to-product/product-sale.response.json");
        server.expect(requestTo("http://p/api/internal/products/42/confirm-sale"))
                .andExpect(content().json(raw("order-to-product/confirm-sale.request.json"), true))
                .andRespond(withSuccess(sold, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://p/api/internal/products/42/sale-refund"))
                .andExpect(content().json(raw("order-to-product/sale-refund.request.json"), true))
                .andRespond(withSuccess(sold, MediaType.APPLICATION_JSON));
        HttpProductServiceClient products = new HttpProductServiceClient(builder, "http://p", "k");
        products.confirmSale(42L, 501L, "ORD-2026-1006", "TXN-20261125-ABCDEFGHJK");
        products.recordRefund(42L, assertSends("order-to-product/sale-refund.request.json", ProductSaleRefundCommand.class));
        server.verify();

        RestClient.Builder repairBuilder = RestClient.builder();
        MockRestServiceServer repair = MockRestServiceServer.bindTo(repairBuilder).build();
        repair.expect(requestTo("http://r/api/internal/repair-requests/7/payment-confirmation"))
                .andExpect(content().json(raw("order-to-repair/payment-confirmation.request.json"), true))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        repair.expect(requestTo("http://r/api/internal/repair-requests/7/payment-events"))
                .andExpect(content().json(raw("order-to-repair/payment-event.refunded.request.json"), true))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        HttpRepairServiceClient repairs = new HttpRepairServiceClient(repairBuilder, "http://r", "k");
        repairs.confirmPayment(7L, assertSends("order-to-repair/payment-confirmation.request.json", PaymentConfirmation.class));
        repairs.reportPaymentEvent(7L, assertSends("order-to-repair/payment-event.refunded.request.json", RepairPaymentEvent.class));
        repair.verify();
    }

    /* ── order-service → repair-service ── */

    @Test
    void repairPaymentMessages() {
        assertSends("order-to-repair/payment-confirmation.request.json", PaymentConfirmation.class);
        RepairPaymentEvent failed = assertSends("order-to-repair/payment-event.failed.request.json", RepairPaymentEvent.class);
        assertThat(failed.eventId()).isEqualTo(failed.transactionId() + ":" + failed.type());
        RepairPaymentEvent refunded = assertSends("order-to-repair/payment-event.refunded.request.json", RepairPaymentEvent.class);
        assertThat(refunded.eventId()).isEqualTo(refunded.transactionId() + ":REFUND:" + refunded.refundedAmountPaise());
    }

    /* ── repair-service → order-service (order-service is the provider) ── */

    @Test
    void quoteApiAcceptsWhatRepairServiceSendsAndReturnsWhatItReads() {
        assertAccepts("repair-to-order/create-quote.request.json", CreateRepairQuoteRequest.class);
        assertAccepts("repair-to-order/quote-decision.request.json", QuoteDecisionRequest.class);
        assertProduces("repair-to-order/repair-quote.response.json", RepairQuoteView.class);
        assertProduces("repair-to-order/order.response.json", InternalOrderView.class);
    }

    private static String raw(String path) throws Exception {
        return Files.readString(Path.of("..", "contracts", path));
    }
}
