package com.reloop.product.marketplace;

import static com.reloop.product.marketplace.MarketplaceTestSupport.createListing;
import static com.reloop.product.marketplace.MarketplaceTestSupport.eventTypes;
import static com.reloop.product.marketplace.MarketplaceTestSupport.registerCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reloop.product.marketplace.MarketplaceTestSupport.Customer;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * order-service's internal sale API: reserve → confirm-sale / release, idempotent per order reference and guarded by
 * the internal API key.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:product-internal-sale-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class InternalProductSaleTest {

    private static final String KEY = "test-internal-key";

    @Autowired
    private MockMvc mvc;

    private Customer seller;
    private Customer buyer;
    private long productId;

    @BeforeEach
    void setUp() throws Exception {
        seller = registerCustomer(mvc, "s-seller");
        buyer = registerCustomer(mvc, "s-buyer");
        productId = createListing(mvc, seller, Map.of("price", 1000));
    }

    private ResultActions call(String action, long id, String json, String key) throws Exception {
        var req = post("/api/internal/products/" + id + "/" + action).contentType(MediaType.APPLICATION_JSON).content(json);
        if (key != null) {
            req.header("X-Internal-Api-Key", key);
        }
        return mvc.perform(req);
    }

    private ResultActions reserve(long id, long buyerId, String order) throws Exception {
        return call("reserve", id, "{\"buyerAccountId\":" + buyerId + ",\"orderReference\":\"" + order + "\"}", KEY);
    }

    private ResultActions confirm(long id, long buyerId, String order) throws Exception {
        return call("confirm-sale", id, "{\"buyerAccountId\":" + buyerId + ",\"orderReference\":\"" + order
                + "\",\"transactionId\":\"TXN-20261125-AAAAAAAAAA\"}", KEY);
    }

    private ResultActions release(long id, String order) throws Exception {
        return call("release", id, "{\"orderReference\":\"" + order + "\",\"reason\":\"Payment window expired\"}", KEY);
    }

    @Test
    void reserveHoldsTheListingAndReturnsPriceAndSeller() throws Exception {
        reserve(productId, buyer.id(), "ORD-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.sellerAccountId").value(seller.id()))
                .andExpect(jsonPath("$.buyerAccountId").value(buyer.id()))
                .andExpect(jsonPath("$.pricePaise").value(100000))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.reservationReference").value("ORD-1"));

        // hidden from browse, visible as RESERVED on the detail page, frozen for the seller
        mvc.perform(get("/api/products/" + productId)).andExpect(jsonPath("$.availability").value("RESERVED"));
        mvc.perform(put("/api/products/" + productId).header("Authorization", seller.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(MarketplaceTestSupport.listingJson(Map.of("price", 900))))
                .andExpect(status().isConflict());
        // repeating the same reservation is idempotent; another order is refused
        reserve(productId, buyer.id(), "ORD-1").andExpect(status().isOk());
        Customer other = registerCustomer(mvc, "s-other");
        reserve(productId, other.id(), "ORD-2").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("reserved by another")));
    }

    @Test
    void confirmSaleTransfersOwnershipOnceAndIsIdempotent() throws Exception {
        reserve(productId, buyer.id(), "ORD-1").andExpect(status().isOk());
        confirm(productId, buyer.id(), "ORD-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD"))
                .andExpect(jsonPath("$.buyerAccountId").value(buyer.id()))
                .andExpect(jsonPath("$.passportCode").exists());
        confirm(productId, buyer.id(), "ORD-1").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SOLD"));

        MvcResult passport = mvc.perform(get("/api/products/" + productId + "/passport"))
                .andExpect(jsonPath("$.ownershipTransfers").value(1)).andReturn();
        assertThat(eventTypes(passport)).containsExactly("LISTED", "RESERVED", "SOLD");
        // released after sale = no-op
        release(productId, "ORD-1").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SOLD"));
    }

    @Test
    void confirmSaleNeedsTheMatchingReservation() throws Exception {
        confirm(productId, buyer.id(), "ORD-1").andExpect(status().isConflict());
        reserve(productId, buyer.id(), "ORD-1").andExpect(status().isOk());
        confirm(productId, buyer.id(), "ORD-OTHER").andExpect(status().isConflict());
    }

    @Test
    void releaseMakesTheListingActiveAgainAndIsIdempotent() throws Exception {
        reserve(productId, buyer.id(), "ORD-1").andExpect(status().isOk());
        release(productId, "ORD-WRONG").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESERVED"));
        release(productId, "ORD-1").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.reservationReference").doesNotExist());
        release(productId, "ORD-1").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));

        MvcResult passport = mvc.perform(get("/api/products/" + productId + "/passport")).andReturn();
        assertThat(eventTypes(passport)).containsExactly("LISTED", "RESERVED", "RESERVATION_RELEASED");
        // a new buyer can reserve it now
        Customer other = registerCustomer(mvc, "s-next");
        reserve(productId, other.id(), "ORD-3").andExpect(status().isOk());
    }

    @Test
    void sellerCannotReserveOwnListingAndUnknownProductsAre404() throws Exception {
        reserve(productId, seller.id(), "ORD-1").andExpect(status().isForbidden());
        reserve(999_999, buyer.id(), "ORD-1").andExpect(status().isNotFound());
    }

    @Test
    void internalApiNeedsTheKey() throws Exception {
        String body = "{\"buyerAccountId\":" + buyer.id() + ",\"orderReference\":\"ORD-1\"}";
        call("reserve", productId, body, null).andExpect(status().isUnauthorized());
        call("reserve", productId, body, "wrong").andExpect(status().isUnauthorized());
        // a customer JWT is not a substitute for the key
        mvc.perform(post("/api/internal/products/" + productId + "/reserve").header("Authorization", buyer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/products/" + productId)).andExpect(jsonPath("$.availability").value("ACTIVE"));
    }

    @Test
    void orderReferenceIsRequired() throws Exception {
        call("reserve", productId, "{\"buyerAccountId\":" + buyer.id() + "}", KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.orderReference").exists());
    }

    /* ───────────────────────────── sale-refund ───────────────────────────── */

    private ResultActions saleRefund(long id, String order, String refundRef, long amount, long total, boolean full)
            throws Exception {
        return call("sale-refund", id, ("{\"orderReference\":\"%s\",\"transactionId\":\"TXN-20261125-AAAAAAAAAA\","
                + "\"refundReference\":\"%s\",\"refundAmountPaise\":%d,\"totalRefundedPaise\":%d,\"fullyRefunded\":%b,"
                + "\"reason\":\"CUSTOMER_REQUEST\"}").formatted(order, refundRef, amount, total, full), KEY);
    }

    @Test
    void saleRefundIsRecordedOnThePassportOncePerRefund() throws Exception {
        reserve(productId, buyer.id(), "ORD-1").andExpect(status().isOk());
        confirm(productId, buyer.id(), "ORD-1").andExpect(status().isOk());

        saleRefund(productId, "ORD-1", "RFD-1", 25_500, 25_500, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD"))
                .andExpect(jsonPath("$.buyerAccountId").value(buyer.id()));
        saleRefund(productId, "ORD-1", "RFD-1", 25_500, 25_500, false).andExpect(status().isOk()); // duplicate delivery
        saleRefund(productId, "ORD-1", "RFD-2", 76_500, 102_000, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD"));

        MvcResult passport = mvc.perform(get("/api/products/" + productId + "/passport")).andReturn();
        assertThat(eventTypes(passport)).containsExactly("LISTED", "RESERVED", "SOLD", "SALE_REFUNDED", "SALE_REFUNDED");
        mvc.perform(get("/api/products/" + productId + "/passport"))
                .andExpect(jsonPath("$.history[3].details").value(org.hamcrest.Matchers.containsString("partly refunded")))
                .andExpect(jsonPath("$.history[4].details").value(org.hamcrest.Matchers.containsString("fully refunded")))
                .andExpect(jsonPath("$.history[4].details").value(org.hamcrest.Matchers.containsString("RFD-2")));
    }

    @Test
    void saleRefundNeedsTheMatchingSaleTheKeyAndAValidBody() throws Exception {
        // not sold yet / sold through another order
        saleRefund(productId, "ORD-1", "RFD-1", 100, 100, true).andExpect(status().isConflict());
        reserve(productId, buyer.id(), "ORD-1").andExpect(status().isOk());
        confirm(productId, buyer.id(), "ORD-1").andExpect(status().isOk());
        saleRefund(productId, "ORD-OTHER", "RFD-1", 100, 100, true).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("was not sold through order ORD-OTHER")));
        saleRefund(999_999, "ORD-1", "RFD-1", 100, 100, true).andExpect(status().isNotFound());

        call("sale-refund", productId, "{\"orderReference\":\"ORD-1\"}", KEY).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.refundReference").exists());
        call("sale-refund", productId, "{\"orderReference\":\"ORD-1\",\"transactionId\":\"T\",\"refundReference\":\"R\","
                + "\"refundAmountPaise\":1,\"totalRefundedPaise\":1,\"fullyRefunded\":true}", null)
                .andExpect(status().isUnauthorized());
    }
}
