package com.reloop.product.auth;

import static com.reloop.product.auth.AuthTestSupport.customerJson;
import static com.reloop.product.auth.AuthTestSupport.postJson;
import static com.reloop.product.auth.AuthTestSupport.profileFor;
import static com.reloop.product.auth.AuthTestSupport.repairerJson;
import static com.reloop.product.auth.AuthTestSupport.token;
import static com.reloop.product.auth.AuthTestSupport.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reloop.product.client.RepairerProfileClient;
import com.reloop.product.controller.CustomerAreaController;
import com.reloop.product.controller.RepairerAreaController;
import com.reloop.product.enums.AccountRole;
import com.reloop.product.security.AuthenticatedAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Requirements 4 and 5: role-based authorization, and one account type can never use the
 * other type's endpoints. Checked at both layers: URL rules (HTTP) and @PreAuthorize (method).
 */
@SpringBootTest(properties = AuthTestSupport.ISOLATED_DB)
@AutoConfigureMockMvc
class RoleBasedAccessTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CustomerAreaController customerArea;

    @Autowired
    private RepairerAreaController repairerArea;

    @MockitoBean
    private RepairerProfileClient repairerProfileClient;

    private String customerToken;
    private String repairerToken;

    @BeforeEach
    void registerOneOfEach() throws Exception {
        when(repairerProfileClient.createProfile(any())).thenAnswer(inv -> profileFor(inv.getArgument(0)));
        customerToken = token(postJson(mvc, "/api/auth/register/customer", customerJson(uniqueEmail("rc"))));
        repairerToken = token(postJson(mvc, "/api/auth/register/repairer", repairerJson(uniqueEmail("rr"))));
    }

    @Test
    void customerCanUseCustomerArea() throws Exception {
        mvc.perform(get("/api/customer/dashboard").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.area").value("customer"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void customerIsForbiddenFromRepairerArea() throws Exception {
        mvc.perform(get("/api/repairer/dashboard").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Your account role is not allowed to use this endpoint"));
    }

    @Test
    void repairerCanUseRepairerArea() throws Exception {
        mvc.perform(get("/api/repairer/dashboard").header("Authorization", "Bearer " + repairerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.area").value("repairer"))
                .andExpect(jsonPath("$.role").value("REPAIRER"));
    }

    @Test
    void repairerIsForbiddenFromCustomerArea() throws Exception {
        mvc.perform(get("/api/customer/dashboard").header("Authorization", "Bearer " + repairerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCallersGet401OnBothAreas() throws Exception {
        mvc.perform(get("/api/customer/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/repairer/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void bothRolesCanReadTheirOwnMe() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CUSTOMER"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + repairerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("REPAIRER"));
    }

    /*
     * Second layer: even if a URL rule were missing, @PreAuthorize still blocks the wrong role.
     * The SecurityContext is set explicitly inside each test (not via @WithMockUser) because
     * the MockMvc calls in @BeforeEach run SecurityContextHolderFilter on this thread, which
     * clears the holder when each request finishes.
     */

    @Test
    void methodSecurityBlocksCustomerFromRepairerController() {
        AuthenticatedAccount customer = new AuthenticatedAccount(1L, "c@example.com", AccountRole.CUSTOMER);
        runAs(customer, () -> {
            assertThatThrownBy(() -> repairerArea.dashboard(customer)).isInstanceOf(AccessDeniedException.class);
            assertThat(customerArea.dashboard(customer)).containsEntry("area", "customer");
        });
    }

    @Test
    void methodSecurityBlocksRepairerFromCustomerController() {
        AuthenticatedAccount repairer = new AuthenticatedAccount(2L, "r@example.com", AccountRole.REPAIRER);
        runAs(repairer, () -> {
            assertThatThrownBy(() -> customerArea.dashboard(repairer)).isInstanceOf(AccessDeniedException.class);
            assertThat(repairerArea.dashboard(repairer)).containsEntry("area", "repairer");
        });
    }

    private static void runAs(AuthenticatedAccount principal, Runnable body) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
        SecurityContextHolder.setContext(context);
        try {
            body.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
