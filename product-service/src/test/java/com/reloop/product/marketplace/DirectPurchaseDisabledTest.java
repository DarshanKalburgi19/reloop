package com.reloop.product.marketplace;

import static com.reloop.product.marketplace.MarketplaceTestSupport.createListing;
import static com.reloop.product.marketplace.MarketplaceTestSupport.registerCustomer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reloop.product.marketplace.MarketplaceTestSupport.Customer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** With the production default the legacy fee-less purchase is switched off (audit C-1). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:product-direct-purchase-off-test;DB_CLOSE_DELAY=-1",
        "reloop.marketplace.direct-purchase-enabled=false"})
@AutoConfigureMockMvc
class DirectPurchaseDisabledTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void directPurchaseAnswers410AndLeavesTheListingActive() throws Exception {
        Customer seller = registerCustomer(mvc, "d-seller");
        Customer buyer = registerCustomer(mvc, "d-buyer");
        long id = createListing(mvc, seller, Map.of("price", 1000));

        mvc.perform(post("/api/products/" + id + "/purchase").header("Authorization", buyer.bearer()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("POST /api/orders")));
        mvc.perform(get("/api/products/" + id)).andExpect(jsonPath("$.availability").value("ACTIVE"));
    }
}
