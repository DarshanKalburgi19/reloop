package com.reloop.product.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reloop.product.client.CreateRepairerProfileCommand;
import com.reloop.product.client.RepairerProfileClient;
import com.reloop.product.client.RepairerProfileView;
import com.reloop.product.entity.Account;
import com.reloop.product.enums.AccountRole;
import com.reloop.product.repository.AccountRepository;
import com.reloop.product.repository.RepairerAccountIdentityRepository;
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
        seeder.run(null);
        assertThat(accounts.count()).isEqualTo(before);
    }
}
