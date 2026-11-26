package com.reloop.product.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reloop.product.client.CreateRepairerProfileCommand;
import com.reloop.product.client.RepairerProfileClient;
import com.reloop.product.client.RepairerProfileView;
import com.jayway.jsonpath.JsonPath;
import com.reloop.product.entity.Account;
import com.reloop.product.entity.Product;
import com.reloop.product.enums.ProductStatus;
import com.reloop.product.enums.AccountRole;
import com.reloop.product.repository.AccountRepository;
import com.reloop.product.repository.ProductRepository;
import com.reloop.product.repository.RepairerAccountIdentityRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Boots product-service with {@code reloop.seed.enabled=true} and proves the documented demo
 * credentials really work. Repair-service is replaced by an in-process stub.
 */
@SpringBootTest(properties = {
        "reloop.seed.enabled=true",
        // production setting (test resources enable it for the legacy-purchase tests): seeding must not need it
        "reloop.marketplace.direct-purchase-enabled=false",
        // own in-memory DB: committed demo rows must not leak into other test classes
        "spring.datasource.url=jdbc:h2:mem:product-seed-test;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@Import(DemoAccountSeederIntegrationTest.StubRepairService.class)
class DemoAccountSeederIntegrationTest {

    /** A stub repair-service that is "up" while the context starts (a Mockito mock would return null). */
    @TestConfiguration
    static class StubRepairService {
        @Bean
        @Primary
        RepairerProfileClient stubRepairerProfileClient() {
            AtomicLong ids = new AtomicLong(9000);
            return new RepairerProfileClient() {
                @Override
                public RepairerProfileView createProfile(CreateRepairerProfileCommand c) {
                    return new RepairerProfileView(ids.incrementAndGet(), c.accountExternalId(), c.businessName(),
                            c.specialties(), c.phone(), c.addressLine(), c.city(), c.pincode(), c.latitude(),
                            c.longitude(), c.serviceHours(), c.acceptsInPerson(), c.acceptsPickupDrop(),
                            c.acceptsShipping(), "UNVERIFIED", "ACTIVE");
                }

                @Override
                public Optional<RepairerProfileView> findProfile(Long profileId) {
                    return Optional.empty();
                }
            };
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private RepairerAccountIdentityRepository identities;

    @Autowired
    private DemoAccountSeeder seeder;

    @Autowired
    private DemoProductSeeder productSeeder;

    @Autowired
    private ProductRepository products;

    @Test
    void demoAccountsExistWithTheRightRoles() {
        Account customer = accounts.findByEmail(DemoAccounts.CUSTOMER_EMAIL).orElseThrow();
        Account repairer = accounts.findByEmail(DemoAccounts.REPAIRER_EMAIL).orElseThrow();
        assertThat(customer.getRole()).isEqualTo(AccountRole.CUSTOMER);
        assertThat(repairer.getRole()).isEqualTo(AccountRole.REPAIRER);
        assertThat(customer.getPasswordHash()).startsWith("$2").isNotEqualTo(DemoAccounts.CUSTOMER_PASSWORD);
        assertThat(identities.findByAccount_Id(repairer.getId())).isPresent();
    }

    @Test
    void documentedCredentialsLogIn() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + DemoAccounts.CUSTOMER_EMAIL + "\",\"password\":\""
                                + DemoAccounts.CUSTOMER_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.role").value("CUSTOMER"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + DemoAccounts.REPAIRER_EMAIL + "\",\"password\":\""
                                + DemoAccounts.REPAIRER_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.role").value("REPAIRER"))
                .andExpect(jsonPath("$.account.repairer.verificationStatus").value("UNVERIFIED"));
    }

    @Test
    void seedingTwiceIsHarmless() throws Exception {
        long before = accounts.count();
        long listings = products.count();
        seeder.run(null);
        productSeeder.run(null);
        assertThat(accounts.count()).isEqualTo(before);
        assertThat(products.count()).isEqualTo(listings).isEqualTo(DemoProductSeeder.LISTINGS.size());
    }

    @Test
    void demoProductsAreSeededInAllFiveCities() throws Exception {
        for (String city : List.of("Ahmedabad", "Surat", "Vadodara", "Rajkot", "Gandhinagar")) {
            mvc.perform(get("/api/products").param("city", city))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(2)));
        }
        // 15 listings, one of them sold during seeding
        mvc.perform(get("/api/products").param("size", "50"))
                .andExpect(jsonPath("$.totalElements").value(DemoProductSeeder.LISTINGS.size() - 1));
    }

    @Test
    void soldDemoProductHasARealPassportHistory() throws Exception {
        Product sold = products.findAll().stream()
                .filter(p -> DemoProductSeeder.SOLD_DEMO_TITLE.equals(p.getTitle()))
                .findFirst().orElseThrow();
        String json = mvc.perform(get("/api/products/" + sold.getId() + "/passport"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownershipTransfers").value(1))
                .andReturn().getResponse().getContentAsString();
        List<String> events = JsonPath.read(json, "$.history[*].eventType");
        // seeded through the real reserve → confirm-sale path, exactly like an order-service sale
        assertThat(events).containsExactly("LISTED", "PRICE_CHANGED", "RESERVED", "SOLD");

        // a sold item can't be bought again, even by a third demo customer
        String sellerLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + DemoAccounts.NISHA_EMAIL + "\",\"password\":\""
                                + DemoAccounts.NISHA_PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(sellerLogin, "$.accessToken");
        mvc.perform(post("/api/products/" + sold.getId() + "/purchase").header("Authorization", "Bearer " + token))
                .andExpect(status().isGone());   // legacy direct purchase is off, as in production
        assertThat(sold.getStatus()).isEqualTo(ProductStatus.SOLD);
        assertThat(sold.getReservationReference()).isEqualTo(DemoProductSeeder.DEMO_SALE_REFERENCE);
    }

    @Test
    void karansStoreListingsShowTheStore() throws Exception {
        mvc.perform(get("/api/products").param("q", "Dell Latitude"))
                .andExpect(jsonPath("$.content[0].storeName").value(DemoProductSeeder.DEMO_STORE_NAME));
    }
}
