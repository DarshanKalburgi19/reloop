package com.reloop.orderpayment.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.reloop.orderpayment.entity.PurchaseOrder;
import com.reloop.orderpayment.enums.OrderStatus;
import com.reloop.orderpayment.exception.ProductServiceException;
import com.reloop.orderpayment.service.OrderService;
import com.reloop.orderpayment.support.ApiTestBase;
import com.reloop.orderpayment.support.Tokens;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** POST/GET /api/orders: reservation in product-service, frozen fees, visibility, cancel and expiry. */
class MarketplaceOrderApiTest extends ApiTestBase {

    @Autowired
    private OrderService orderService;

    @Test
    void placingAnOrderReservesTheProductAndFreezesTheFees() throws Exception {
        JsonNode order = body(postJson("/api/orders", Tokens.customer(BUYER), "{\"productId\":" + PRODUCT + "}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/orders/")))
                .andExpect(jsonPath("$.orderNumber").value(org.hamcrest.Matchers.matchesPattern("ORD-\\d{8}-[0-9A-Z]{10}")))
                .andExpect(jsonPath("$.transactionType").value("MARKETPLACE_PURCHASE"))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.buyerAccountId").value(BUYER))
                .andExpect(jsonPath("$.sellerAccountId").value(SELLER))
                .andExpect(jsonPath("$.productId").value(PRODUCT))
                .andExpect(jsonPath("$.productTitle").value("Used Sony headphones"))
                // the user's example: ₹1,000 item → ₹1,020 to pay, seller nets ₹980, ReLoop earns ₹40
                .andExpect(jsonPath("$.subtotalPaise").value(100000))
                .andExpect(jsonPath("$.customerFeeRate").value(0.02))
                .andExpect(jsonPath("$.customerFeePaise").value(2000))
                .andExpect(jsonPath("$.totalPaise").value(102000))
                .andExpect(jsonPath("$.providerCommissionRate").value(0.02))
                .andExpect(jsonPath("$.providerCommissionPaise").value(2000))
                .andExpect(jsonPath("$.sellerNetPaise").value(98000))
                .andExpect(jsonPath("$.platformRevenuePaise").value(4000))
                .andExpect(jsonPath("$.amountDuePaise").value(102000))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.payments").isEmpty()));

        verify(productService).reserve(eq(PRODUCT), eq(BUYER), eq(order.get("orderNumber").asText()));
        PurchaseOrder saved = orders.findById(order.get("id").asLong()).orElseThrow();
        assertThat(saved.getCustomerFeeRate()).isEqualByComparingTo("0.02");
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(25));
    }

    @Test
    void placingTheSameOrderTwiceReturnsTheOpenOrder() throws Exception {
        JsonNode first = placeOrder(BUYER, PRODUCT);
        postJson("/api/orders", Tokens.customer(BUYER), "{\"productId\":" + PRODUCT + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first.get("id").asLong()));
        verify(productService, times(1)).reserve(anyLong(), anyLong(), anyString());
        assertThat(orders.count()).isEqualTo(1);
    }

    @Test
    void productServiceRefusalsArePassedOnAndNoOrderIsCreated() throws Exception {
        when(productService.reserve(eq(43L), anyLong(), anyString()))
                .thenThrow(new ProductServiceException(409, "Product is not available for purchase (availability: SOLD)", null));
        when(productService.reserve(eq(44L), anyLong(), anyString()))
                .thenThrow(new ProductServiceException(403, "You cannot purchase your own listing", null));
        when(productService.reserve(eq(45L), anyLong(), anyString()))
                .thenThrow(new ProductServiceException(503, "Product service is unavailable", null));
        when(productService.reserve(eq(46L), anyLong(), anyString()))
                .thenThrow(new ProductServiceException(404, "Product 46 not found", null));

        postJson("/api/orders", Tokens.customer(BUYER), "{\"productId\":43}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("SOLD")));
        postJson("/api/orders", Tokens.customer(BUYER), "{\"productId\":44}").andExpect(status().isForbidden());
        postJson("/api/orders", Tokens.customer(BUYER), "{\"productId\":45}").andExpect(status().isServiceUnavailable());
        postJson("/api/orders", Tokens.customer(BUYER), "{\"productId\":46}").andExpect(status().isNotFound());
        assertThat(orders.count()).isZero();
    }

    @Test
    void orderValidationAndRoles() throws Exception {
        postJson("/api/orders", Tokens.customer(BUYER), "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.productId").exists());
        postJson("/api/orders", Tokens.repairer(BUYER), "{\"productId\":42}").andExpect(status().isForbidden());
        postJson("/api/orders", null, "{\"productId\":42}").andExpect(status().isUnauthorized());
        verify(productService, never()).reserve(anyLong(), anyLong(), anyString());
    }

    @Test
    void buyerAndSellerCanReadTheOrderOthersGet404() throws Exception {
        long id = placeOrder(BUYER, PRODUCT).get("id").asLong();

        mvc.perform(get("/api/orders/{id}", id).header("Authorization", Tokens.customer(BUYER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/orders/{id}", id).header("Authorization", Tokens.customer(SELLER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sellerNetPaise").value(98000));
        mvc.perform(get("/api/orders/{id}", id).header("Authorization", Tokens.customer(STRANGER)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/orders/{id}", id)).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/orders").header("Authorization", Tokens.customer(BUYER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/orders?role=seller").header("Authorization", Tokens.customer(SELLER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/orders").header("Authorization", Tokens.customer(STRANGER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void buyerCanCancelAnUnpaidOrderAndTheProductIsReleased() throws Exception {
        JsonNode order = placeOrder(BUYER, PRODUCT);
        long id = order.get("id").asLong();

        postJson("/api/orders/" + id + "/cancel", Tokens.customer(SELLER), "{}").andExpect(status().isNotFound());
        postJson("/api/orders/" + id + "/cancel", Tokens.customer(BUYER), "{\"reason\":\"Changed my mind\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Changed my mind"))
                .andExpect(jsonPath("$.amountDuePaise").value(0));
        verify(productService).release(eq(PRODUCT), eq(order.get("orderNumber").asText()), any());
        assertThat(orders.findById(id).orElseThrow().isProductReleased()).isTrue();

        // a cancelled order can't be paid
        payOrder(BUYER, id, "UPI", "SUCCESS", 102_000).andExpect(status().isConflict())
                .andExpect(jsonPath("$.orderStatus").value("CANCELLED"));
    }

    @Test
    void paidOrdersCannotBeCancelled() throws Exception {
        JsonNode order = placeOrder(BUYER, PRODUCT);
        payOrder(BUYER, order.get("id").asLong(), "UPI", "SUCCESS", 102_000).andExpect(status().isCreated());
        postJson("/api/orders/" + order.get("id").asLong() + "/cancel", Tokens.customer(BUYER), "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("refund")));
    }

    @Test
    void expiredUnpaidOrdersAreCancelledAndReleased() throws Exception {
        JsonNode order = placeOrder(BUYER, PRODUCT);
        long id = order.get("id").asLong();
        PurchaseOrder o = orders.findById(id).orElseThrow();
        o.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        orders.save(o);

        // paying after the window closed is refused
        payOrder(BUYER, id, "UPI", "SUCCESS", 102_000).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("payment window")));
        assertThat(payments.count()).isZero();

        assertThat(orderService.expireDueOrders()).isEqualTo(1);
        PurchaseOrder expired = orders.findById(id).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(expired.getCancelReason()).isEqualTo("Payment window expired");
        assertThat(expired.isProductReleased()).isTrue();
        verify(productService).release(eq(PRODUCT), eq(order.get("orderNumber").asText()), eq("Payment window expired"));
        assertThat(orderService.expireDueOrders()).isZero();
    }

    @Test
    void failedReleaseIsRetriedByTheNextSweep() throws Exception {
        long id = placeOrder(BUYER, PRODUCT).get("id").asLong();
        PurchaseOrder o = orders.findById(id).orElseThrow();
        o.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        orders.save(o);
        org.mockito.Mockito.doThrow(new ProductServiceException(503, "down", null))
                .when(productService).release(eq(PRODUCT), anyString(), any());

        orderService.expireDueOrders();
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orders.findById(id).orElseThrow().isProductReleased()).isFalse();

        org.mockito.Mockito.doReturn(new com.reloop.orderpayment.client.ProductSale(PRODUCT, "Used Sony headphones", SELLER,
                null, 100_000L, "ACTIVE", "RLP-TEST-0001", null))
                .when(productService).release(eq(PRODUCT), anyString(), any());   // product-service is back
        orderService.expireDueOrders();
        assertThat(orders.findById(id).orElseThrow().isProductReleased()).isTrue();
    }
}
