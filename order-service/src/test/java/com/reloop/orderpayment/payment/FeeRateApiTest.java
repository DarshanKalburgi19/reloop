package com.reloop.orderpayment.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.reloop.orderpayment.support.ApiTestBase;
import com.reloop.orderpayment.support.Tokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Fee rates come from application.properties unless overridden in the database; a change applies to NEW orders and
 * quotes only, because every order / quote / payment stores the exact rate it was created with.
 */
class FeeRateApiTest extends ApiTestBase {

    private org.springframework.test.web.servlet.ResultActions setRates(String type, String body, String key) throws Exception {
        var req = put("/api/internal/fee-rates/{type}", type).contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) {
            req.header("X-Internal-Api-Key", key);
        }
        return mvc.perform(req);
    }

    @Test
    void defaultsComeFromApplicationProperties() throws Exception {
        mvc.perform(get("/api/fee-rates").header("Authorization", Tokens.customer(BUYER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].transactionType").value("MARKETPLACE_PURCHASE"))
                .andExpect(jsonPath("$[0].customerFeeRate").value(0.02))
                .andExpect(jsonPath("$[0].providerCommissionRate").value(0.02))
                .andExpect(jsonPath("$[0].source").value("APPLICATION_PROPERTIES"))
                .andExpect(jsonPath("$[1].transactionType").value("REPAIR_BOOKING"));
    }

    @Test
    void databaseOverrideAppliesToNewOrdersOnlyAndExistingOrdersKeepTheirRate() throws Exception {
        JsonNode before = placeOrder(BUYER, PRODUCT);   // 2% + 2%

        setRates("MARKETPLACE_PURCHASE", "{\"customerFeeRate\":0.03,\"providerCommissionRate\":0.05,\"note\":\"Festive test\"}", KEY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("DATABASE"))
                .andExpect(jsonPath("$.customerFeeRate").value(0.03))
                .andExpect(jsonPath("$.note").value("Festive test"));

        // the existing order is paid with its frozen 2% numbers
        payOrder(BUYER, before.get("id").asLong(), "UPI", "SUCCESS", 102_000)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerFeeRate").value(0.02))
                .andExpect(jsonPath("$.platformRevenuePaise").value(4000));

        // a new order uses the new rates: ₹1,000 → fee ₹30, commission ₹50, seller ₹950, ReLoop ₹80
        stubProduct(43L, SELLER, 100_000L, "Second item");
        postJson("/api/orders", Tokens.customer(BUYER), "{\"productId\":43}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerFeeRate").value(0.03))
                .andExpect(jsonPath("$.customerFeePaise").value(3000))
                .andExpect(jsonPath("$.totalPaise").value(103000))
                .andExpect(jsonPath("$.providerCommissionPaise").value(5000))
                .andExpect(jsonPath("$.sellerNetPaise").value(95000))
                .andExpect(jsonPath("$.platformRevenuePaise").value(8000));

        // repair bookings are configured separately and still use the defaults
        long quoteId = repairQuote(5001, 100_000);
        mvc.perform(get("/api/internal/repair-quotes/{id}", quoteId).header("X-Internal-Api-Key", KEY))
                .andExpect(jsonPath("$.customerFeePaise").value(2000));

        mvc.perform(delete("/api/internal/fee-rates/MARKETPLACE_PURCHASE").header("X-Internal-Api-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("APPLICATION_PROPERTIES"))
                .andExpect(jsonPath("$.customerFeeRate").value(0.02));
    }

    @Test
    void changingRatesNeedsTheInternalKeyAndSaneValues() throws Exception {
        String body = "{\"customerFeeRate\":0.03,\"providerCommissionRate\":0.03}";
        setRates("MARKETPLACE_PURCHASE", body, null).andExpect(status().isUnauthorized());
        setRates("MARKETPLACE_PURCHASE", body, "wrong").andExpect(status().isUnauthorized());
        setRates("MARKETPLACE_PURCHASE", "{\"customerFeeRate\":0.9,\"providerCommissionRate\":0.03}", KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.customerFeeRate").exists());
        setRates("MARKETPLACE_PURCHASE", "{\"customerFeeRate\":-0.01,\"providerCommissionRate\":0.03}", KEY)
                .andExpect(status().isBadRequest());
        setRates("MARKETPLACE_PURCHASE", "{\"customerFeeRate\":0.01}", KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.providerCommissionRate").exists());
        setRates("SOMETHING_ELSE", body, KEY).andExpect(status().isBadRequest());
    }
}
