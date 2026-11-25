package com.reloop.product.marketplace;

import static com.reloop.product.marketplace.MarketplaceTestSupport.createListing;
import static com.reloop.product.marketplace.MarketplaceTestSupport.eventTypes;
import static com.reloop.product.marketplace.MarketplaceTestSupport.jsonPost;
import static com.reloop.product.marketplace.MarketplaceTestSupport.listingJson;
import static com.reloop.product.marketplace.MarketplaceTestSupport.read;
import static com.reloop.product.marketplace.MarketplaceTestSupport.registerCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reloop.product.entity.Account;
import com.reloop.product.enums.AccountRole;
import com.reloop.product.marketplace.MarketplaceTestSupport.Customer;
import com.reloop.product.repository.AccountRepository;
import com.reloop.product.security.JwtService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Buying (features 8 and 9) and the passport across owners (feature 10):
 * own listings and unavailable listings can't be bought, and concurrent buyers can't both win.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:product-marketplace-purchase-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ProductPurchaseTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AccountRepository accounts;

    private Customer seller;
    private Customer buyer;
    private long productId;

    @BeforeEach
    void setUp() throws Exception {
        seller = registerCustomer(mvc, "p-seller");
        buyer = registerCustomer(mvc, "p-buyer");
        productId = createListing(mvc, seller, Map.of("price", 1500));
    }

    private MvcResult buy(long id, Customer as) throws Exception {
        return mvc.perform(post("/api/products/" + id + "/purchase").header("Authorization", as.bearer())).andReturn();
    }

    @Test
    void customerCanBuyAnotherCustomersListing() throws Exception {
        mvc.perform(post("/api/products/" + productId + "/purchase").header("Authorization", buyer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.sellerId").value(seller.id()))
                .andExpect(jsonPath("$.buyerId").value(buyer.id()))
                .andExpect(jsonPath("$.pricePaise").value(150000))
                .andExpect(jsonPath("$.availability").value("SOLD"))
                .andExpect(jsonPath("$.passportCode").exists())
                .andExpect(jsonPath("$.purchasedAt").exists());

        mvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value("SOLD"))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.soldAt").exists());

        MvcResult passport = mvc.perform(get("/api/products/" + productId + "/passport"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownershipTransfers").value(1))
                .andExpect(jsonPath("$.history[1].actorRole").value("BUYER"))
                .andExpect(jsonPath("$.history[1].price").value(1500.0))
                .andReturn();
        assertThat(eventTypes(passport)).containsExactly("LISTED", "SOLD");
        assertThat(passport.getResponse().getContentAsString())
                .doesNotContain(buyer.email()).doesNotContain(seller.email());
    }

    @Test
    void sellerCannotBuyOwnListing() throws Exception {
        mvc.perform(post("/api/products/" + productId + "/purchase").header("Authorization", seller.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You cannot purchase your own listing"));
        mvc.perform(get("/api/products/" + productId)).andExpect(jsonPath("$.availability").value("ACTIVE"));
    }

    @Test
    void soldListingCannotBeBoughtAgain() throws Exception {
        assertThat(buy(productId, buyer).getResponse().getStatus()).isEqualTo(200);
        Customer third = registerCustomer(mvc, "p-third");

        mvc.perform(post("/api/products/" + productId + "/purchase").header("Authorization", third.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Product is not available for purchase (availability: SOLD)"));
        mvc.perform(post("/api/products/" + productId + "/purchase").header("Authorization", buyer.bearer()))
                .andExpect(status().isConflict());
    }

    @Test
    void deactivatedListingCannotBeBought() throws Exception {
        mvc.perform(delete("/api/products/" + productId).header("Authorization", seller.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/products/" + productId + "/purchase").header("Authorization", buyer.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Product is not available for purchase (availability: ARCHIVED)"));
    }

    @Test
    void unknownProductIs404() throws Exception {
        mvc.perform(post("/api/products/987654321/purchase").header("Authorization", buyer.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyAuthenticatedCustomersCanBuy() throws Exception {
        mvc.perform(post("/api/products/" + productId + "/purchase")).andExpect(status().isUnauthorized());

        Account repairer = accounts.saveAndFlush(new Account("rep-" + System.nanoTime() + "@example.com", "x", AccountRole.REPAIRER));
        String token = jwtService.issue(repairer.getId(), repairer.getEmail(), AccountRole.REPAIRER).token();
        mvc.perform(post("/api/products/" + productId + "/purchase").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/products/" + productId)).andExpect(jsonPath("$.availability").value("ACTIVE"));
    }

    @Test
    void soldListingsAreFrozen() throws Exception {
        buy(productId, buyer);
        mvc.perform(put("/api/products/" + productId).header("Authorization", seller.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(listingJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Sold listings cannot be edited"));
        mvc.perform(delete("/api/products/" + productId).header("Authorization", seller.bearer()))
                .andExpect(status().isConflict());
    }

    @Test
    void concurrentBuyersCannotBothWin() throws Exception {
        List<Customer> buyers = List.of(buyer, registerCustomer(mvc, "p-racer1"), registerCustomer(mvc, "p-racer2"));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(buyers.size());
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (Customer b : buyers) {
                Callable<Integer> attempt = () -> {
                    start.await();
                    return buy(productId, b).getResponse().getStatus();
                };
                results.add(pool.submit(attempt));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) {
                statuses.add(f.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
            assertThat(statuses).filteredOn(s -> s == 409).hasSize(buyers.size() - 1);
        } finally {
            pool.shutdownNow();
        }
        MvcResult passport = mvc.perform(get("/api/products/" + productId + "/passport")).andReturn();
        assertThat(eventTypes(passport)).containsExactly("LISTED", "SOLD");
    }

    /* ───────────────────── resale: passport follows the item ───────────────────── */

    @Test
    void buyerCanRelistAndThePassportKeepsTheWholeHistory() throws Exception {
        String passportCode = read(buy(productId, buyer), "$.passportCode");

        MvcResult relisted = mvc.perform(jsonPost("/api/products",
                        listingJson(Map.of("passportCode", passportCode.toLowerCase(), "price", 1200,
                                "condition", "FAIR")), buyer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passportCode").value(passportCode))
                .andExpect(jsonPath("$.sellerId").value(buyer.id()))
                .andReturn();
        long relistedId = ((Number) read(relisted, "$.id")).longValue();

        // the original seller can now buy it back: the item changes hands a second time
        assertThat(buy(relistedId, seller).getResponse().getStatus()).isEqualTo(200);

        MvcResult passport = mvc.perform(get("/api/products/" + relistedId + "/passport"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passportCode").value(passportCode))
                .andExpect(jsonPath("$.listingCount").value(2))
                .andExpect(jsonPath("$.ownershipTransfers").value(2))
                .andReturn();
        assertThat(eventTypes(passport)).containsExactly("LISTED", "SOLD", "RELISTED", "SOLD");
        List<Number> listingIds = read(passport, "$.listingIds");
        assertThat(listingIds.stream().map(Number::longValue)).containsExactly(productId, relistedId);
        // the same passport is visible from the first listing too
        mvc.perform(get("/api/products/" + productId + "/passport"))
                .andExpect(jsonPath("$.ownershipTransfers").value(2));
    }

    @Test
    void onlyTheCurrentOwnerCanRelist() throws Exception {
        String passportCode = read(buy(productId, buyer), "$.passportCode");
        mvc.perform(jsonPost("/api/products", listingJson(Map.of("passportCode", passportCode)), seller))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only the current owner of this item can list it"));
    }

    @Test
    void anItemCanHaveOnlyOneOpenListing() throws Exception {
        String passportCode = read(buy(productId, buyer), "$.passportCode");
        mvc.perform(jsonPost("/api/products", listingJson(Map.of("passportCode", passportCode)), buyer))
                .andExpect(status().isCreated());
        mvc.perform(jsonPost("/api/products", listingJson(Map.of("passportCode", passportCode)), buyer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This item already has an open listing"));
    }

    @Test
    void unknownPassportCodeIsAValidationError() throws Exception {
        mvc.perform(jsonPost("/api/products", listingJson(Map.of("passportCode", "RLP-0000-0000-0000")), buyer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.passportCode").value("unknown passport code"));
    }

    @Test
    void sellerCannotReactivateAfterSellingTheItemThroughAnotherListing() throws Exception {
        // seller deactivates listing A, the passport stays with them, so they can't relist while A is open...
        mvc.perform(delete("/api/products/" + productId).header("Authorization", seller.bearer()));
        String code = read(mvc.perform(get("/api/products/" + productId).header("Authorization", seller.bearer()))
                .andReturn(), "$.passportCode");
        long listingB = createListing(mvc, seller, Map.of("passportCode", code));
        assertThat(buy(listingB, buyer).getResponse().getStatus()).isEqualTo(200);

        // ...and once the item is sold through listing B, listing A can't come back
        mvc.perform(put("/api/products/" + productId).header("Authorization", seller.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(listingJson(Map.of("status", "ACTIVE"))))
                .andExpect(status().isConflict());
    }
}
