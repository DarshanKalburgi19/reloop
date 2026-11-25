package com.reloop.product.marketplace;

import static com.reloop.product.marketplace.MarketplaceTestSupport.createListing;
import static com.reloop.product.marketplace.MarketplaceTestSupport.read;
import static com.reloop.product.marketplace.MarketplaceTestSupport.registerCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reloop.product.marketplace.MarketplaceTestSupport.Customer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Browse, keyword search, filters, sorting and paging (features 4-6) on a fixed data set:
 * 6 ACTIVE listings, plus 1 deactivated and 1 sold listing that must never appear.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:product-marketplace-browse-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductBrowseTest {

    @Autowired
    private MockMvc mvc;

    @BeforeAll
    void listings() throws Exception {
        Customer seller = registerCustomer(mvc, "browse-seller");
        Customer buyer = registerCustomer(mvc, "browse-buyer");

        createListing(mvc, seller, listing("Sony Bravia 43 inch TV", "Full HD smart TV with remote",
                "ELECTRONICS", 18000, "VERY_GOOD", "Ahmedabad"));
        createListing(mvc, seller, listing("Vintage Sony Walkman", "Cassette player, works fine",
                "ELECTRONICS", 2500, "GOOD", "Surat"));
        createListing(mvc, seller, listing("Teak wood dining table", "Six seater, comes with chairs",
                "FURNITURE", 12000, "GOOD", "Vadodara"));
        createListing(mvc, seller, listing("Harry Potter box set", "All seven books, hardly read",
                "BOOKS", 1500, "LIKE_NEW", "Rajkot"));
        createListing(mvc, seller, listing("Office chair", "Ergonomic, 100% working recline",
                "FURNITURE", 3000, "FAIR", "Ahmedabad"));
        createListing(mvc, seller, listing("Samsung fridge", "Not cooling, needs a new compressor",
                "APPLIANCES", 4000, "NEEDS_REPAIR", "Gandhinagar"));

        long archived = createListing(mvc, seller, listing("Sony PlayStation 4", "Two controllers",
                "ELECTRONICS", 15000, "GOOD", "Ahmedabad"));
        mvc.perform(delete("/api/products/" + archived).header("Authorization", seller.bearer()))
                .andExpect(status().isNoContent());

        long sold = createListing(mvc, seller, listing("Sony camera lens", "50 mm prime",
                "ELECTRONICS", 9000, "GOOD", "Ahmedabad"));
        mvc.perform(post("/api/products/" + sold + "/purchase").header("Authorization", buyer.bearer()))
                .andExpect(status().isOk());
    }

    private static Map<String, Object> listing(String title, String description, String category, int price,
                                               String condition, String city) {
        return Map.of("title", title, "description", description, "category", category, "price", price,
                "condition", condition, "city", city);
    }

    private List<String> titles(String... params) throws Exception {
        var request = get("/api/products");
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return read(result, "$.content[*].title");
    }

    @Test
    void browseShowsOnlyAvailableListingsAnonymously() throws Exception {
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content[0].availability").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].passportCode").exists())
                .andExpect(jsonPath("$.content[0].thumbnailUrl").exists());
        assertThat(titles()).doesNotContain("Sony PlayStation 4", "Sony camera lens");
    }

    @Test
    void newestFirstByDefault() throws Exception {
        assertThat(titles().get(0)).isEqualTo("Samsung fridge");
        assertThat(titles("sort", "oldest").get(0)).isEqualTo("Sony Bravia 43 inch TV");
    }

    @Test
    void keywordSearchCoversTitleAndDescriptionCaseInsensitively() throws Exception {
        assertThat(titles("q", "SONY")).containsExactlyInAnyOrder("Sony Bravia 43 inch TV", "Vintage Sony Walkman");
        assertThat(titles("q", "chairs")).containsExactly("Teak wood dining table");      // description only
        assertThat(titles("q", "compressor")).containsExactly("Samsung fridge");
        assertThat(titles("q", "sony walkman")).containsExactly("Vintage Sony Walkman"); // every word must match
        assertThat(titles("q", "nothing-like-this")).isEmpty();
    }

    @Test
    void likeWildcardsInKeywordsAreMatchedLiterally() throws Exception {
        assertThat(titles("q", "100%")).containsExactly("Office chair");
        assertThat(titles("q", "_")).isEmpty(); // unescaped, "_" would match every listing
    }

    @Test
    void filterByCategory() throws Exception {
        assertThat(titles("category", "FURNITURE"))
                .containsExactlyInAnyOrder("Teak wood dining table", "Office chair");
    }

    @Test
    void filterByCityIsCaseInsensitive() throws Exception {
        assertThat(titles("city", "ahmedabad")).containsExactlyInAnyOrder("Sony Bravia 43 inch TV", "Office chair");
        assertThat(titles("city", "Gandhinagar")).containsExactly("Samsung fridge");
    }

    @Test
    void filterByCondition() throws Exception {
        assertThat(titles("condition", "GOOD"))
                .containsExactlyInAnyOrder("Vintage Sony Walkman", "Teak wood dining table");
        assertThat(titles("condition", "NEEDS_REPAIR")).containsExactly("Samsung fridge");
    }

    @Test
    void filterByPriceRangeIsInclusive() throws Exception {
        assertThat(titles("minPrice", "2500", "maxPrice", "4000"))
                .containsExactlyInAnyOrder("Vintage Sony Walkman", "Office chair", "Samsung fridge");
        assertThat(titles("minPrice", "12000")).containsExactlyInAnyOrder("Sony Bravia 43 inch TV", "Teak wood dining table");
        assertThat(titles("maxPrice", "1500")).containsExactly("Harry Potter box set");
    }

    @Test
    void filtersCombineWithAnd() throws Exception {
        assertThat(titles("category", "FURNITURE", "city", "Ahmedabad")).containsExactly("Office chair");
        assertThat(titles("q", "sony", "maxPrice", "5000")).containsExactly("Vintage Sony Walkman");
        assertThat(titles("category", "BOOKS", "city", "Surat")).isEmpty();
    }

    @Test
    void sortByPrice() throws Exception {
        assertThat(titles("sort", "price_asc")).first().isEqualTo("Harry Potter box set");
        assertThat(titles("sort", "price_desc")).first().isEqualTo("Sony Bravia 43 inch TV");
    }

    @Test
    void pagination() throws Exception {
        mvc.perform(get("/api/products").param("size", "4").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void invalidSearchParametersAre400() throws Exception {
        mvc.perform(get("/api/products").param("category", "SPACESHIPS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mvc.perform(get("/api/products").param("minPrice", "5000").param("maxPrice", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.priceRangeValid").value("minPrice must not be greater than maxPrice"));
        mvc.perform(get("/api/products").param("size", "500")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("sort", "random")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("minPrice", "-1")).andExpect(status().isBadRequest());
    }
}
