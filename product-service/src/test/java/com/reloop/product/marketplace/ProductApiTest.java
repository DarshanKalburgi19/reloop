package com.reloop.product.marketplace;

import static com.reloop.product.marketplace.MarketplaceTestSupport.createListing;
import static com.reloop.product.marketplace.MarketplaceTestSupport.eventTypes;
import static com.reloop.product.marketplace.MarketplaceTestSupport.jsonPost;
import static com.reloop.product.marketplace.MarketplaceTestSupport.listingJson;
import static com.reloop.product.marketplace.MarketplaceTestSupport.read;
import static com.reloop.product.marketplace.MarketplaceTestSupport.registerCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reloop.product.entity.Account;
import com.reloop.product.entity.Store;
import com.reloop.product.enums.AccountRole;
import com.reloop.product.marketplace.MarketplaceTestSupport.Customer;
import com.reloop.product.repository.AccountRepository;
import com.reloop.product.repository.StoreRepository;
import com.reloop.product.security.JwtService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Listing CRUD, validation, ownership, roles, images and passport history (features 1-3, 7, 10). */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:product-marketplace-api-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ProductApiTest {

    private static final String PASSPORT_FORMAT = "^RLP-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}$";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private StoreRepository stores;

    @Autowired
    private AccountRepository accounts;

    private Customer seller;
    private Customer other;

    @BeforeEach
    void customers() throws Exception {
        seller = registerCustomer(mvc, "seller");
        other = registerCustomer(mvc, "other");
    }

    /* ───────────────────────────── create ───────────────────────────── */

    @Test
    void createReturns201WithAllProductFields() throws Exception {
        mvc.perform(jsonPost("/api/products", listingJson(Map.of("city", "surat")), seller))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/products/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.sellerId").value(seller.id()))
                .andExpect(jsonPath("$.sellerName").value("seller Tester"))
                .andExpect(jsonPath("$.title").value("Used Sony headphones"))
                .andExpect(jsonPath("$.description").value("Good sound, light scratches on the headband."))
                .andExpect(jsonPath("$.category").value("ELECTRONICS"))
                .andExpect(jsonPath("$.price").value(2499.5))
                .andExpect(jsonPath("$.pricePaise").value(249950))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.condition").value("GOOD"))
                .andExpect(jsonPath("$.city").value("surat"))
                .andExpect(jsonPath("$.pickupLocation").value("Adajan, near Star Bazaar"))
                .andExpect(jsonPath("$.availability").value("ACTIVE"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.passportCode", matchesPattern(PASSPORT_FORMAT)))
                .andExpect(jsonPath("$.createdAt").exists())
                // no image given -> category placeholder
                .andExpect(jsonPath("$.images", hasSize(1)))
                .andExpect(jsonPath("$.images[0].url").value("/demo-images/placeholders/electronics.svg"))
                .andExpect(jsonPath("$.images[0].primary").value(true))
                .andExpect(jsonPath("$.images[0].placeholder").value(true));
    }

    @Test
    void sellerContactDataIsNeverExposed() throws Exception {
        MvcResult result = mvc.perform(jsonPost("/api/products", listingJson(), seller)).andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(seller.email()).doesNotContain("9876543210").doesNotContain("password");
    }

    @Test
    void knownCityNamesAreCanonicalised() throws Exception {
        // "Ahmedabad" exists in the cities table (created by customer registration)
        mvc.perform(jsonPost("/api/products", listingJson(Map.of("city", "  AHMEDABAD ")), seller))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.city").value("Ahmedabad"));
    }

    @Test
    void customImagesKeepTheirOrderAndFirstIsPrimary() throws Exception {
        List<String> urls = List.of("/demo-images/placeholders/books.svg", "https://images.example.com/p/1.jpg");
        mvc.perform(jsonPost("/api/products", listingJson(Map.of("imageUrls", urls)), seller))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.images", hasSize(2)))
                .andExpect(jsonPath("$.images[0].url").value(urls.get(0)))
                .andExpect(jsonPath("$.images[0].primary").value(true))
                .andExpect(jsonPath("$.images[1].url").value(urls.get(1)))
                .andExpect(jsonPath("$.images[1].primary").value(false))
                .andExpect(jsonPath("$.images[1].placeholder").value(false));
    }

    @Test
    void createValidatesEveryField() throws Exception {
        Map<String, Object> bad = new HashMap<>();
        bad.put("title", "");
        bad.put("description", null);
        bad.put("price", 0);
        bad.put("condition", null);
        bad.put("city", " ");
        bad.put("imageUrls", List.of("javascript:alert(1)"));
        mvc.perform(jsonPost("/api/products", listingJson(bad), seller))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists())
                .andExpect(jsonPath("$.fieldErrors.price").exists())
                .andExpect(jsonPath("$.fieldErrors.condition").exists())
                .andExpect(jsonPath("$.fieldErrors.city").exists())
                .andExpect(jsonPath("$.fieldErrors['imageUrls[0]']").exists());
    }

    @Test
    void priceWithMoreThanTwoDecimalsIsRejected() throws Exception {
        mvc.perform(jsonPost("/api/products", listingJson(Map.of("price", 10.999)), seller))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.price").exists());
    }

    @Test
    void imagePathTraversalAndTooManyImagesAreRejected() throws Exception {
        mvc.perform(jsonPost("/api/products",
                        listingJson(Map.of("imageUrls", List.of("/demo-images/../application.properties"))), seller))
                .andExpect(status().isBadRequest());
        List<String> nine = java.util.Collections.nCopies(9, "/demo-images/placeholders/other.svg");
        mvc.perform(jsonPost("/api/products", listingJson(Map.of("imageUrls", nine)), seller))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.imageUrls").exists());
    }

    @Test
    void unknownEnumValueIsRejected() throws Exception {
        mvc.perform(jsonPost("/api/products", listingJson(Map.of("condition", "BRAND_NEW")), seller))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousCannotCreateAndRepairerIsForbidden() throws Exception {
        mvc.perform(jsonPost("/api/products", listingJson(), null))
                .andExpect(status().isUnauthorized());

        Account repairer = accounts.saveAndFlush(new Account("rep-" + System.nanoTime() + "@example.com", "x", AccountRole.REPAIRER));
        String repairerToken = jwtService.issue(repairer.getId(), repairer.getEmail(), AccountRole.REPAIRER).token();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/products")
                        .header("Authorization", "Bearer " + repairerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(listingJson()))
                .andExpect(status().isForbidden());
    }

    /* ───────────────────────────── stores ───────────────────────────── */

    @Test
    void listingFromOwnStoreShowsTheStore() throws Exception {
        Store store = newStore(seller.id(), "Seller's Shop");
        mvc.perform(jsonPost("/api/products", listingJson(Map.of("storeId", store.getId())), seller))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.store.id").value(store.getId()))
                .andExpect(jsonPath("$.store.name").value("Seller's Shop"));
    }

    @Test
    void someoneElsesStoreIsRejected() throws Exception {
        Store store = newStore(other.id(), "Not yours");
        mvc.perform(jsonPost("/api/products", listingJson(Map.of("storeId", store.getId())), seller))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.storeId").value("you can only list products in your own store"));
    }

    /* ───────────────────────────── read ───────────────────────────── */

    @Test
    void detailsArePublicAndUnknownIdsAre404() throws Exception {
        long id = createListing(mvc, seller, Map.of());
        mvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/products/987654321"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mvc.perform(get("/api/products/not-a-number"))
                .andExpect(status().isNotFound());
    }

    @Test
    void placeholderImagesAreServedPublicly() throws Exception {
        mvc.perform(get("/demo-images/placeholders/electronics.svg"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/svg+xml"))
                .andExpect(content().string(containsString("ReLoop placeholder")));
    }

    /* ───────────────────────────── edit ───────────────────────────── */

    @Test
    void sellerCanEditOwnListingAndHistoryIsRecorded() throws Exception {
        long id = createListing(mvc, seller, Map.of());
        String update = listingJson(Map.of("title", "Sony WH-CH510 headphones", "price", 1999, "condition", "FAIR"));

        mvc.perform(put("/api/products/" + id).header("Authorization", seller.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Sony WH-CH510 headphones"))
                .andExpect(jsonPath("$.pricePaise").value(199900))
                .andExpect(jsonPath("$.condition").value("FAIR"));

        MvcResult passport = mvc.perform(get("/api/products/" + id + "/passport"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history[1].eventType").value("PRICE_CHANGED"))
                .andExpect(jsonPath("$.history[1].details").value("Price changed from \u20B92,499.50 to \u20B91,999.00"))
                .andExpect(jsonPath("$.history[2].details").value("Updated: title, condition"))
                .andReturn();
        assertThat(eventTypes(passport)).containsExactly("LISTED", "PRICE_CHANGED", "UPDATED");
    }

    @Test
    void unchangedEditRecordsNoHistory() throws Exception {
        long id = createListing(mvc, seller, Map.of());
        mvc.perform(put("/api/products/" + id).header("Authorization", seller.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(listingJson()))
                .andExpect(status().isOk());
        assertThat(eventTypes(mvc.perform(get("/api/products/" + id + "/passport")).andReturn()))
                .containsExactly("LISTED");
    }

    @Test
    void otherCustomersCannotEditOrDelete() throws Exception {
        long id = createListing(mvc, seller, Map.of());
        mvc.perform(put("/api/products/" + id).header("Authorization", other.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(listingJson(Map.of("price", 1))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only edit your own listings"));
        mvc.perform(delete("/api/products/" + id).header("Authorization", other.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only delete your own listings"));
        mvc.perform(get("/api/products/" + id)).andExpect(jsonPath("$.pricePaise").value(249950));
    }

    @Test
    void statusInUpdateMayOnlyBeActiveOrArchived() throws Exception {
        long id = createListing(mvc, seller, Map.of());
        mvc.perform(put("/api/products/" + id).header("Authorization", seller.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(listingJson(Map.of("status", "SOLD"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").value("must be ACTIVE or ARCHIVED"));
    }

    /* ───────────────────────── delete / deactivate ───────────────────────── */

    @Test
    void deleteDeactivatesAndHidesTheListingFromOthers() throws Exception {
        long id = createListing(mvc, seller, Map.of("title", "Deactivate me please"));

        mvc.perform(delete("/api/products/" + id).header("Authorization", seller.bearer()))
                .andExpect(status().isNoContent());
        // idempotent
        mvc.perform(delete("/api/products/" + id).header("Authorization", seller.bearer()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/products/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/" + id).header("Authorization", other.bearer())).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/" + id + "/passport")).andExpect(status().isNotFound());
        // other customers can't even learn that it exists
        mvc.perform(put("/api/products/" + id).header("Authorization", other.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(listingJson()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/products").param("q", "Deactivate me please"))
                .andExpect(jsonPath("$.totalElements").value(0));

        // the seller still sees it, with its history kept
        mvc.perform(get("/api/products/" + id).header("Authorization", seller.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value("ARCHIVED"))
                .andExpect(jsonPath("$.available").value(false));
        assertThat(eventTypes(mvc.perform(get("/api/products/" + id + "/passport")
                .header("Authorization", seller.bearer())).andReturn()))
                .containsExactly("LISTED", "DEACTIVATED");
    }

    @Test
    void sellerCanReactivateADeactivatedListing() throws Exception {
        long id = createListing(mvc, seller, Map.of());
        mvc.perform(put("/api/products/" + id).header("Authorization", seller.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(listingJson(Map.of("status", "ARCHIVED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value("ARCHIVED"));
        mvc.perform(put("/api/products/" + id).header("Authorization", seller.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(listingJson(Map.of("status", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value("ACTIVE"));
        assertThat(eventTypes(mvc.perform(get("/api/products/" + id + "/passport")).andReturn()))
                .containsExactly("LISTED", "DEACTIVATED", "REACTIVATED");
    }

    /* ───────────────────────────── mine ───────────────────────────── */

    @Test
    void mineListsOwnListingsInEveryStatus() throws Exception {
        long active = createListing(mvc, seller, Map.of());
        long archived = createListing(mvc, seller, Map.of());
        createListing(mvc, other, Map.of());
        mvc.perform(delete("/api/products/" + archived).header("Authorization", seller.bearer()));

        MvcResult result = mvc.perform(get("/api/products/mine").header("Authorization", seller.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn();
        List<Number> ids = read(result, "$.content[*].id");
        assertThat(ids.stream().map(Number::longValue)).containsExactlyInAnyOrder(active, archived);

        mvc.perform(get("/api/products/mine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/products/mine").param("size", "500").header("Authorization", seller.bearer()))
                .andExpect(status().isBadRequest());
    }

    private Store newStore(long ownerId, String name) {
        Store store = new Store();
        store.setOwner(accounts.findById(ownerId).orElseThrow());
        store.setName(name);
        store.setAddressLine("1 Market Street");
        return stores.saveAndFlush(store);
    }
}
