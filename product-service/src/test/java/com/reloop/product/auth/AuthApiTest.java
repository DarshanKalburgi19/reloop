package com.reloop.product.auth;

import static com.reloop.product.auth.AuthTestSupport.CUSTOMER_PASSWORD;
import static com.reloop.product.auth.AuthTestSupport.REPAIRER_PASSWORD;
import static com.reloop.product.auth.AuthTestSupport.customerJson;
import static com.reloop.product.auth.AuthTestSupport.loginJson;
import static com.reloop.product.auth.AuthTestSupport.postJson;
import static com.reloop.product.auth.AuthTestSupport.profileFor;
import static com.reloop.product.auth.AuthTestSupport.repairerJson;
import static com.reloop.product.auth.AuthTestSupport.token;
import static com.reloop.product.auth.AuthTestSupport.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.reloop.product.client.CreateRepairerProfileCommand;
import com.reloop.product.client.RepairerProfileClient;
import com.reloop.product.client.RepairerProfileView;
import com.reloop.product.entity.Account;
import com.reloop.product.enums.AccountStatus;
import com.reloop.product.exception.RepairServiceException;
import com.reloop.product.repository.AccountRepository;
import com.reloop.product.repository.RepairerAccountIdentityRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end tests of /api/auth/* through the real security filter chain, controllers,
 * validation, services and H2. Only repair-service is replaced by a Mockito mock.
 */
@SpringBootTest(properties = AuthTestSupport.ISOLATED_DB)
@AutoConfigureMockMvc
class AuthApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private RepairerAccountIdentityRepository identityRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private RepairerProfileClient repairerProfileClient;

    @BeforeEach
    void healthyRepairService() {
        when(repairerProfileClient.createProfile(any())).thenAnswer(inv -> profileFor(inv.getArgument(0)));
    }

    /* ─────────────────────────── customer registration ─────────────────────────── */

    @Test
    void registerCustomerReturns201WithTokenAndNoPasswordData() throws Exception {
        String email = uniqueEmail("cust");
        MvcResult result = mvc.perform(post("/api/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON).content(customerJson(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.expiresInSeconds").value(3600))
                .andExpect(jsonPath("$.account.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.account.status").value("ACTIVE"))
                .andExpect(jsonPath("$.account.email").value(email))
                .andExpect(jsonPath("$.account.name").value("Asha Patel"))
                .andExpect(jsonPath("$.account.phone").value("+919812345678"))
                .andExpect(jsonPath("$.account.customer.firstName").value("Asha"))
                .andExpect(jsonPath("$.account.customer.lastName").value("Patel"))
                .andExpect(jsonPath("$.account.customer.address").value("12 Riverfront Road"))
                .andExpect(jsonPath("$.account.customer.city").value("Ahmedabad"))
                .andExpect(jsonPath("$.account.customer.pincode").value("380009"))
                .andExpect(jsonPath("$.account.repairer").doesNotExist())
                .andReturn();

        assertNoPasswordData(result.getResponse().getContentAsString());
    }

    @Test
    void passwordIsStoredAsBcryptHashNeverPlainText() throws Exception {
        String email = uniqueEmail("hash");
        postJson(mvc, "/api/auth/register/customer", customerJson(email));

        Account saved = accountRepository.findByEmail(email).orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo(CUSTOMER_PASSWORD);
        assertThat(saved.getPasswordHash()).matches("^\\$2[aby]\\$\\d{2}\\$.{53}$");
        assertThat(passwordEncoder.matches(CUSTOMER_PASSWORD, saved.getPasswordHash())).isTrue();
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        String email = uniqueEmail("dup");
        assertThat(postJson(mvc, "/api/auth/register/customer", customerJson(email)).getResponse().getStatus())
                .isEqualTo(201);

        mvc.perform(post("/api/auth/register/customer").contentType(MediaType.APPLICATION_JSON)
                        .content(customerJson(email.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("An account with this email already exists"));
    }

    @Test
    void duplicateEmailIsRejectedAcrossAccountTypes() throws Exception {
        String email = uniqueEmail("cross");
        postJson(mvc, "/api/auth/register/customer", customerJson(email));

        mvc.perform(post("/api/auth/register/repairer").contentType(MediaType.APPLICATION_JSON)
                        .content(repairerJson(email)))
                .andExpect(status().isConflict());
        verify(repairerProfileClient, never()).createProfile(any());
    }

    @Test
    void invalidCustomerPayloadReturns400WithFieldErrors() throws Exception {
        String body = """
            {"name":"","email":"not-an-email","phone":"12ab","password":"short",
             "address":"x","city":"","pincode":"12"}
            """;
        mvc.perform(post("/api/auth/register/customer").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.city").exists())
                .andExpect(jsonPath("$.fieldErrors.pincode").exists());
    }

    @Test
    void passwordWithoutDigitIsRejected() throws Exception {
        String body = customerJson(uniqueEmail("weak")).replace(CUSTOMER_PASSWORD, "onlyletters");
        mvc.perform(post("/api/auth/register/customer").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value("must contain at least one letter and one digit"));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/auth/register/customer").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    /* ─────────────────────────── repairer registration ─────────────────────────── */

    @Test
    void registerRepairerReturns201AndIsAlwaysUnverified() throws Exception {
        String email = uniqueEmail("rep");
        // the fixture sends "verificationStatus":"VERIFIED" - it must be ignored
        MvcResult result = mvc.perform(post("/api/auth/register/repairer")
                        .contentType(MediaType.APPLICATION_JSON).content(repairerJson(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.account.role").value("REPAIRER"))
                .andExpect(jsonPath("$.account.customer").doesNotExist())
                .andExpect(jsonPath("$.account.repairer.verificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.account.repairer.repairerProfileId", notNullValue()))
                .andExpect(jsonPath("$.account.repairer.workshop.workshopName").value("Mehta Fix-It"))
                .andExpect(jsonPath("$.account.repairer.workshop.repairExpertise",
                        containsInAnyOrder("Mobile phones", "Laptops")))
                .andExpect(jsonPath("$.account.repairer.workshop.latitude").value(23.0225))
                .andExpect(jsonPath("$.account.repairer.workshop.serviceHours").value("Mon-Sat 10:00-19:00"))
                .andExpect(jsonPath("$.account.repairer.workshop.repairMethods",
                        containsInAnyOrder("IN_PERSON", "SEND_ITEM")))
                .andReturn();
        assertNoPasswordData(result.getResponse().getContentAsString());

        ArgumentCaptor<CreateRepairerProfileCommand> sent = ArgumentCaptor.forClass(CreateRepairerProfileCommand.class);
        verify(repairerProfileClient).createProfile(sent.capture());
        CreateRepairerProfileCommand cmd = sent.getValue();
        Long accountId = accountRepository.findByEmail(email).orElseThrow().getId();
        assertThat(cmd.accountExternalId()).isEqualTo(accountId);
        assertThat(cmd.acceptsInPerson()).isTrue();
        assertThat(cmd.acceptsShipping()).isTrue();      // SEND_ITEM
        assertThat(cmd.acceptsPickupDrop()).isFalse();   // PICKUP_DROPOFF not chosen
        assertThat(cmd.specialties()).isEqualTo("Mobile phones, Laptops");
        assertThat(identityRepository.findByAccount_Id(accountId)).isPresent();
    }

    @Test
    void unknownRepairMethodIsRejected() throws Exception {
        mvc.perform(post("/api/auth/register/repairer").contentType(MediaType.APPLICATION_JSON)
                        .content(repairerJson(uniqueEmail("tp"), "[\"TELEPORT\"]", 23.0)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void repairerValidationCoversCoordinatesAndMethods() throws Exception {
        mvc.perform(post("/api/auth/register/repairer").contentType(MediaType.APPLICATION_JSON)
                        .content(repairerJson(uniqueEmail("geo"), "[]", 123.0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.latitude").exists())
                .andExpect(jsonPath("$.fieldErrors.repairMethods").exists());
        verify(repairerProfileClient, never()).createProfile(any());
    }

    @Test
    void repairerRegistrationRollsBackWhenRepairServiceIsDown() throws Exception {
        when(repairerProfileClient.createProfile(any()))
                .thenThrow(new RepairServiceException(503, "Repair service is unavailable", null));
        String email = uniqueEmail("down");

        mvc.perform(post("/api/auth/register/repairer").contentType(MediaType.APPLICATION_JSON)
                        .content(repairerJson(email)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));

        assertThat(accountRepository.existsByEmail(email)).as("no half-registered account").isFalse();
    }

    /* ─────────────────────────────────── login ─────────────────────────────────── */

    @Test
    void bothAccountTypesLogInThroughTheSameEndpoint() throws Exception {
        String customer = uniqueEmail("lc");
        String repairer = uniqueEmail("lr");
        postJson(mvc, "/api/auth/register/customer", customerJson(customer));
        postJson(mvc, "/api/auth/register/repairer", repairerJson(repairer));

        MvcResult c = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(customer.toUpperCase(), CUSTOMER_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.account.role").value("CUSTOMER"))
                .andReturn();
        assertNoPasswordData(c.getResponse().getContentAsString());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(repairer, REPAIRER_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.role").value("REPAIRER"))
                .andExpect(jsonPath("$.account.repairer.verificationStatus").value("UNVERIFIED"));
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSame401() throws Exception {
        String email = uniqueEmail("wp");
        postJson(mvc, "/api/auth/register/customer", customerJson(email));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "Wrong1password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(uniqueEmail("nobody"), "Whatever1pass")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void suspendedAccountCannotLogIn() throws Exception {
        String email = uniqueEmail("susp");
        postJson(mvc, "/api/auth/register/customer", customerJson(email));
        Account account = accountRepository.findByEmail(email).orElseThrow();
        account.setStatus(AccountStatus.SUSPENDED);
        accountRepository.save(account);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, CUSTOMER_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Account is SUSPENDED and cannot sign in"));
    }

    @Test
    void loginValidatesInput() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    /* ──────────────────────────────────── /me ──────────────────────────────────── */

    @Test
    void meRequiresAToken() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/auth/me"));
    }

    @Test
    void meRejectsGarbageAndTamperedTokens() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired access token"));

        String email = uniqueEmail("tamper");
        String token = token(postJson(mvc, "/api/auth/register/customer", customerJson(email)));
        String[] parts = token.split("\\.");
        // swap the payload for one claiming the REPAIRER role; the signature no longer matches
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"1\",\"role\":\"REPAIRER\",\"iss\":\"reloop-product-service\"}".getBytes());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + parts[0] + "." + forgedPayload + "." + parts[2]))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsTheCustomerProfileWithoutPasswordData() throws Exception {
        String email = uniqueEmail("mec");
        String token = token(postJson(mvc, "/api/auth/register/customer", customerJson(email)));

        MvcResult result = mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.customer.city").value("Ahmedabad"))
                .andExpect(jsonPath("$.customer.state").value("Gujarat"))
                .andExpect(jsonPath("$.repairer").doesNotExist())
                .andReturn();
        assertNoPasswordData(result.getResponse().getContentAsString());
    }

    @Test
    void meReturnsTheRepairerProfileIncludingLiveWorkshopData() throws Exception {
        String email = uniqueEmail("mer");
        MvcResult reg = postJson(mvc, "/api/auth/register/repairer", repairerJson(email));
        String body = reg.getResponse().getContentAsString();
        Number profileId = JsonPath.read(body, "$.account.repairer.repairerProfileId");
        RepairerProfileView view = new RepairerProfileView(profileId.longValue(), 1L, "Mehta Fix-It",
                "Mobile phones, Laptops", "9898989898", "5 CG Road", "Ahmedabad", "380006", 23.0225, 72.5714,
                "Mon-Sat 10:00-19:00", true, true, false, "UNVERIFIED", "ACTIVE");
        when(repairerProfileClient.findProfile(anyLong())).thenReturn(Optional.of(view));

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token(reg)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("REPAIRER"))
                .andExpect(jsonPath("$.repairer.repairerProfileId").value(profileId.longValue()))
                .andExpect(jsonPath("$.repairer.verificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.repairer.workshop.repairMethods",
                        containsInAnyOrder("IN_PERSON", "PICKUP_DROPOFF")))
                .andExpect(jsonPath("$.customer").doesNotExist());
    }

    @Test
    void meStillWorksWhenRepairServiceIsDown() throws Exception {
        String email = uniqueEmail("meo");
        String token = token(postJson(mvc, "/api/auth/register/repairer", repairerJson(email)));
        when(repairerProfileClient.findProfile(anyLong())).thenReturn(Optional.empty());

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repairer.verificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.repairer.workshop").doesNotExist());
    }

    private static void assertNoPasswordData(String json) {
        assertThat(json.toLowerCase()).doesNotContain("password");
        assertThat(json).doesNotContain("$2a$").doesNotContain("$2b$").doesNotContain("$2y$");
        assertThat(json).doesNotContain(CUSTOMER_PASSWORD).doesNotContain(REPAIRER_PASSWORD);
    }
}
