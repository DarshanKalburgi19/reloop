package com.reloop.orderpayment.payment;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.reloop.orderpayment.support.ApiTestBase;
import com.reloop.orderpayment.support.Tokens;
import org.junit.jupiter.api.Test;

/** GET /api/internal/orders/{id}: the order lookup repair-service uses for store-to-customer delivery tracking. */
class InternalOrderApiTest extends ApiTestBase {

    @Test
    void returnsBuyerSellerAndStatusWithoutAmounts() throws Exception {
        JsonNode order = placeOrder(BUYER, PRODUCT);
        long id = order.get("id").asLong();

        mvc.perform(get("/api/internal/orders/{id}", id).header("X-Internal-Api-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.orderNumber").value(order.get("orderNumber").asText()))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.buyerAccountId").value(BUYER))
                .andExpect(jsonPath("$.sellerAccountId").value(SELLER))
                .andExpect(jsonPath("$.productId").value(PRODUCT))
                .andExpect(jsonPath("$.productTitle").value("Used Sony headphones"))
                .andExpect(jsonPath("$.paidAt").value(nullValue()))
                .andExpect(jsonPath("$.totalPaise").doesNotExist())
                .andExpect(jsonPath("$.payments").doesNotExist());
    }

    @Test
    void paidOrderIsConfirmed() throws Exception {
        JsonNode order = placeOrder(BUYER, PRODUCT);
        long id = order.get("id").asLong();
        payOrder(BUYER, id, "UPI", "SUCCESS", order.get("totalPaise").asLong()).andExpect(status().isCreated());

        mvc.perform(get("/api/internal/orders/{id}", id).header("X-Internal-Api-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paidAt").exists());
    }

    @Test
    void needsTheInternalKey() throws Exception {
        long id = placeOrder(BUYER, PRODUCT).get("id").asLong();

        mvc.perform(get("/api/internal/orders/{id}", id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/internal/orders/{id}", id).header("X-Internal-Api-Key", "wrong"))
                .andExpect(status().isUnauthorized());
        // a user JWT is not a substitute for the service key
        mvc.perform(get("/api/internal/orders/{id}", id).header("Authorization", Tokens.customer(BUYER)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownOrderIsNotFound() throws Exception {
        mvc.perform(get("/api/internal/orders/{id}", 987654).header("X-Internal-Api-Key", KEY))
                .andExpect(status().isNotFound());
    }
}
