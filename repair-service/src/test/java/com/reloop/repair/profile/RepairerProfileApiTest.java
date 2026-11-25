package com.reloop.repair.profile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Tests the internal profile-creation endpoint used by product-service during repairer registration. */
@SpringBootTest(properties = {
        "reloop.internal.api-key=test-internal-key",
        // own in-memory DB: these tests commit rows; keep them away from RepairSchemaTest
        "spring.datasource.url=jdbc:h2:mem:repair-profile-api-test;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class RepairerProfileApiTest {

    private static final String KEY = "test-internal-key";
    private static final AtomicLong ACCOUNT_IDS = new AtomicLong(500);

    @Autowired
    private MockMvc mvc;

    private static String body(long accountId, double latitude, boolean inPerson, boolean pickup, boolean shipping) {
        return """
            {"accountExternalId":%d,"businessName":"Mehta Fix-It","specialties":"Mobile phones, Laptops",
             "phone":"9898989898","addressLine":"5 CG Road","city":"Ahmedabad","pincode":"380006",
             "latitude":%s,"longitude":72.5714,"serviceHours":"Mon-Sat 10:00-19:00",
             "acceptsInPerson":%s,"acceptsPickupDrop":%s,"acceptsShipping":%s,"verificationStatus":"VERIFIED"}
            """.formatted(accountId, latitude, inPerson, pickup, shipping);
    }

    private static String validBody(long accountId) {
        return body(accountId, 23.0225, true, false, true);
    }

    @Test
    void createWithInternalKeyReturns201AndUnverifiedProfile() throws Exception {
        long accountId = ACCOUNT_IDS.incrementAndGet();
        mvc.perform(post("/api/repairer-profiles").header("X-Internal-Api-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody(accountId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.accountExternalId").value(accountId))
                .andExpect(jsonPath("$.businessName").value("Mehta Fix-It"))
                .andExpect(jsonPath("$.acceptsInPerson").value(true))
                .andExpect(jsonPath("$.acceptsPickupDrop").value(false))
                .andExpect(jsonPath("$.acceptsShipping").value(true))
                .andExpect(jsonPath("$.verificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void missingOrWrongKeyReturns401BeforeValidation() throws Exception {
        mvc.perform(post("/api/repairer-profiles")
                        .contentType(MediaType.APPLICATION_JSON).content(validBody(ACCOUNT_IDS.incrementAndGet())))
                .andExpect(status().isUnauthorized());
        // invalid body + wrong key -> still 401 (callers without the key learn nothing)
        mvc.perform(post("/api/repairer-profiles").header("X-Internal-Api-Key", "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(body(1, 999, true, false, false)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Missing or invalid X-Internal-Api-Key"));
    }

    @Test
    void invalidBodyWithKeyReturns400() throws Exception {
        mvc.perform(post("/api/repairer-profiles").header("X-Internal-Api-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ACCOUNT_IDS.incrementAndGet(), 95.0, true, false, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.latitude").exists());
    }

    @Test
    void atLeastOneRepairMethodIsRequired() throws Exception {
        mvc.perform(post("/api/repairer-profiles").header("X-Internal-Api-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ACCOUNT_IDS.incrementAndGet(), 23.0, false, false, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("At least one repair method must be accepted"));
    }

    @Test
    void secondProfileForSameAccountReturns409() throws Exception {
        long accountId = ACCOUNT_IDS.incrementAndGet();
        mvc.perform(post("/api/repairer-profiles").header("X-Internal-Api-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody(accountId)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/repairer-profiles").header("X-Internal-Api-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody(accountId)))
                .andExpect(status().isConflict());
    }

    @Test
    void profileCanBeReadBackAndMissingIs404() throws Exception {
        String created = mvc.perform(post("/api/repairer-profiles").header("X-Internal-Api-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody(ACCOUNT_IDS.incrementAndGet())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(created, "$.id");

        mvc.perform(get("/api/repairer-profiles/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Ahmedabad"))
                .andExpect(jsonPath("$.serviceHours").value("Mon-Sat 10:00-19:00"));
        mvc.perform(get("/api/repairer-profiles/987654"))
                .andExpect(status().isNotFound());
    }
}
