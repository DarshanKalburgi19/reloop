package com.reloop.repair.repairer;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Workshop profile CRUD, authorization and validation for {@code /api/repairers}. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:repairer-api-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class RepairerApiTest {

    private static final AtomicLong ACCOUNT_IDS = new AtomicLong(2000);

    @Autowired
    private MockMvc mvc;

    private ResultActions create(String auth, String body) throws Exception {
        var request = post("/api/repairers").contentType(MediaType.APPLICATION_JSON).content(body);
        if (auth != null) {
            request.header(HttpHeaders.AUTHORIZATION, auth);
        }
        return mvc.perform(request);
    }

    private long createdProfile(long accountId, String name) throws Exception {
        String json = create(TestTokens.repairer(accountId), ProfileJson.valid(name))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    /* ─────────────────────────── create / read ─────────────────────────── */

    @Test
    void repairerCreatesAProfileThatIsListedAndPublic() throws Exception {
        long account = ACCOUNT_IDS.incrementAndGet();
        String json = create(TestTokens.repairer(account), ProfileJson.valid("Navrangpura Phone Lab"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/repairers/")))
                .andExpect(jsonPath("$.businessName").value("Navrangpura Phone Lab"))
                .andExpect(jsonPath("$.city").value("Ahmedabad"))
                .andExpect(jsonPath("$.categories", hasSize(2)))
                .andExpect(jsonPath("$.expertise[0]").value("Screen replacement"))
                .andExpect(jsonPath("$.services", hasSize(3)))
                .andExpect(jsonPath("$.startingPriceLabel").value("From ₹499"))
                .andExpect(jsonPath("$.latitude").value(23.0276))
                .andExpect(jsonPath("$.longitude").value(72.5577))
                .andExpect(jsonPath("$.location.lat").value(23.0276))
                .andExpect(jsonPath("$.location.lng").value(72.5577))
                .andExpect(jsonPath("$.mapUrl", containsString("openstreetmap.org")))
                .andExpect(jsonPath("$.workingHoursSummary").value("Mon-Sat 10:00-19:00, Sun closed"))
                .andExpect(jsonPath("$.repairMethods", hasSize(2)))
                .andExpect(jsonPath("$.repairMethods", hasItem("SEND_ITEM")))
                .andExpect(jsonPath("$.availability").value("AVAILABLE"))
                .andExpect(jsonPath("$.rating.count").value(0))
                .andExpect(jsonPath("$.verificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.listedInSearch").value(true))
                .andExpect(jsonPath("$.missingForListing", hasSize(0)))
                .andExpect(jsonPath("$.ownedByViewer").value(true))
                .andExpect(jsonPath("$.demo").value(false))
                .andExpect(jsonPath("$.demoNotice").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(json, "$.id")).longValue();
        List<String> labels = JsonPath.read(json, "$.services[*].priceLabel");
        org.assertj.core.api.Assertions.assertThat(labels).contains("From ₹1,499", "₹899.50", "₹499");

        // anonymous customers can read it; ownedByViewer is false for them
        mvc.perform(get("/api/repairers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Navrangpura Phone Lab"))
                .andExpect(jsonPath("$.ownedByViewer").value(false));
        mvc.perform(get("/api/repairers/me").header(HttpHeaders.AUTHORIZATION, TestTokens.repairer(account)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        // and it appears in search
        mvc.perform(get("/api/repairers").param("q", "navrangpura phone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem((int) id)));
    }

    @Test
    void secondProfileForTheSameAccountIs409WithTheExistingId() throws Exception {
        long account = ACCOUNT_IDS.incrementAndGet();
        long id = createdProfile(account, "First Workshop");
        create(TestTokens.repairer(account), ProfileJson.valid("Second Workshop"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.profileId").value(id));
    }

    @Test
    void unknownProfileIs404() throws Exception {
        mvc.perform(get("/api/repairers/{id}", 987654)).andExpect(status().isNotFound());
        mvc.perform(get("/api/repairers/me").header(HttpHeaders.AUTHORIZATION, TestTokens.repairer(ACCOUNT_IDS.incrementAndGet())))
                .andExpect(status().isNotFound());
    }

    /* ─────────────────────────── authorization ─────────────────────────── */

    @Test
    void writesNeedARepairerToken() throws Exception {
        String body = ProfileJson.valid("No Token Workshop");
        create(null, body).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("Bearer")));
        create(TestTokens.customer(ACCOUNT_IDS.incrementAndGet()), body).andExpect(status().isForbidden());
        mvc.perform(get("/api/repairers/me").header(HttpHeaders.AUTHORIZATION, TestTokens.customer(ACCOUNT_IDS.incrementAndGet())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/repairers/me")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/repairers/{id}", 1).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidExpiredOrForeignTokensAre401() throws Exception {
        String body = ProfileJson.valid("Bad Token Workshop");
        long account = ACCOUNT_IDS.incrementAndGet();
        create("Bearer not-a-jwt", body).andExpect(status().isUnauthorized());
        create(TestTokens.bearer(TestTokens.mint(account, "REPAIRER", TestTokens.SECRET, TestTokens.ISSUER,
                Instant.now().minusSeconds(60)))).andExpect(status().isUnauthorized());
        create(TestTokens.bearer(TestTokens.mint(account, "REPAIRER", "some-other-secret-0123456789-abcdefghijkl",
                TestTokens.ISSUER, Instant.now().plusSeconds(600))), body).andExpect(status().isUnauthorized());
        create(TestTokens.bearer(TestTokens.mint(account, "REPAIRER", TestTokens.SECRET, "someone-else",
                Instant.now().plusSeconds(600))), body).andExpect(status().isUnauthorized());
        // an invalid token on a public endpoint is ignored: search still works
        mvc.perform(get("/api/repairers").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isOk());
    }

    private ResultActions create(String auth) throws Exception {
        return create(auth, ProfileJson.valid("Bad Token Workshop"));
    }

    @Test
    void onlyTheOwnerCanEdit() throws Exception {
        long owner = ACCOUNT_IDS.incrementAndGet();
        long id = createdProfile(owner, "Owner Workshop");
        mvc.perform(put("/api/repairers/{id}", id).header(HttpHeaders.AUTHORIZATION, TestTokens.repairer(ACCOUNT_IDS.incrementAndGet()))
                        .contentType(MediaType.APPLICATION_JSON).content(ProfileJson.valid("Hijacked Workshop")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/repairers/{id}", id).header(HttpHeaders.AUTHORIZATION, TestTokens.customer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(ProfileJson.valid("Hijacked Workshop")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/repairers/{id}", id)).andExpect(jsonPath("$.businessName").value("Owner Workshop"));
    }

    /* ─────────────────────────────── update ─────────────────────────────── */

    @Test
    void updateReplacesFieldsKeepsReferencedServicesAndDeactivatesRemovedOnes() throws Exception {
        long owner = ACCOUNT_IDS.incrementAndGet();
        String auth = TestTokens.repairer(owner);
        String created = create(auth, ProfileJson.valid("Edit Me Workshop")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();
        List<Integer> mobileIds = JsonPath.read(created, "$.services[?(@.category == 'MOBILE')].id");
        int keptId = mobileIds.get(0);

        String updated = ProfileJson.withServices("Edited Workshop", """
            [{"id":%d,"name":"Phone screen replacement","category":"MOBILE","priceType":"STARTING_FROM","price":1299},
             {"name":"Tempered glass fitting","category":"MOBILE","priceType":"FIXED","price":199}]""".formatted(keptId));
        updated = ProfileJson.replace(updated, "\"repairMethods\":[\"IN_PERSON\",\"SEND_ITEM\"]", "\"repairMethods\":[\"PICKUP_DROPOFF\"]");
        updated = ProfileJson.replace(updated, "\"availability\":\"AVAILABLE\"", "\"availability\":\"BUSY\"");

        mvc.perform(put("/api/repairers/{id}", id).header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content(updated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Edited Workshop"))
                .andExpect(jsonPath("$.services", hasSize(2)))
                .andExpect(jsonPath("$.services[*].id", hasItem(keptId)))
                .andExpect(jsonPath("$.services[*].name", not(hasItem("Laptop OS reinstall"))))
                .andExpect(jsonPath("$.startingPriceLabel").value("From ₹199"))
                .andExpect(jsonPath("$.repairMethods", hasSize(1)))
                .andExpect(jsonPath("$.repairMethods[0]").value("PICKUP_DROPOFF"))
                .andExpect(jsonPath("$.availability").value("BUSY"));

        // a service id from another workshop is rejected
        long otherId = createdProfile(ACCOUNT_IDS.incrementAndGet(), "Other Workshop");
        String other = mvc.perform(get("/api/repairers/{id}", otherId)).andReturn().getResponse().getContentAsString();
        int foreignServiceId = JsonPath.read(other, "$.services[0].id");
        String stealing = ProfileJson.withServices("Edited Workshop", """
            [{"id":%d,"name":"Phone screen replacement","category":"MOBILE","priceType":"FIXED","price":100}]"""
                .formatted(foreignServiceId));
        mvc.perform(put("/api/repairers/{id}", id).header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content(stealing))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['services[0].id']").value("unknown service id for this workshop"));
    }

    @Test
    void registrationProfileIsHiddenUntilTheRepairerCompletesIt() throws Exception {
        long account = ACCOUNT_IDS.incrementAndGet();
        String internal = mvc.perform(post("/api/repairer-profiles").header("X-Internal-Api-Key", "test-internal-key")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                            {"accountExternalId":%d,"businessName":"Fresh Signup Repairs","specialties":"Phones",
                             "phone":"9898989898","addressLine":"5 Ring Road","city":"Surat","pincode":"395002",
                             "latitude":21.1702,"longitude":72.8311,"serviceHours":"Mon-Sat",
                             "acceptsInPerson":true,"acceptsPickupDrop":false,"acceptsShipping":false}
                            """.formatted(account)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(internal, "$.id")).longValue();
        String auth = TestTokens.repairer(account);

        mvc.perform(get("/api/repairers/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/repairers/{id}", id).header(HttpHeaders.AUTHORIZATION, TestTokens.repairer(ACCOUNT_IDS.incrementAndGet())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/repairers/me").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.listedInSearch").value(false))
                .andExpect(jsonPath("$.missingForListing", hasItem("description")))
                .andExpect(jsonPath("$.missingForListing", hasItem("services")))
                .andExpect(jsonPath("$.missingForListing", hasItem("workingHours")));
        // another repairer can't edit it, and can't even tell that it exists
        mvc.perform(put("/api/repairers/{id}", id).header(HttpHeaders.AUTHORIZATION, TestTokens.repairer(ACCOUNT_IDS.incrementAndGet()))
                        .contentType(MediaType.APPLICATION_JSON).content(ProfileJson.valid("Hijack")))
                .andExpect(status().isNotFound());
        // POST is a conflict: the account already has a profile; edit it with PUT
        create(auth, ProfileJson.valid("Fresh Signup Repairs")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.profileId").value(id));

        mvc.perform(put("/api/repairers/{id}", id).header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content(ProfileJson.valid("Fresh Signup Repairs")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listedInSearch").value(true))
                .andExpect(jsonPath("$.verificationStatus").value("UNVERIFIED"));
        mvc.perform(get("/api/repairers/{id}", id)).andExpect(status().isOk());
    }

    /* ───────────────────────────── validation ───────────────────────────── */

    @Test
    void missingAndMalformedFieldsAre400WithFieldErrors() throws Exception {
        String auth = TestTokens.repairer(ACCOUNT_IDS.incrementAndGet());
        String valid = ProfileJson.valid("Validation Workshop");

        create(auth, ProfileJson.replace(valid, "\"businessName\":\"Validation Workshop\"", "\"businessName\":\" \""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.businessName").exists());
        create(auth, ProfileJson.replace(valid, "\"pincode\":\"380009\"", "\"pincode\":\"38009\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.pincode").value("must be a 6-digit Indian PIN code"));
        create(auth, ProfileJson.replace(valid, "\"repairMethods\":[\"IN_PERSON\",\"SEND_ITEM\"]", "\"repairMethods\":[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.repairMethods").exists());
        create(auth, ProfileJson.replace(valid, "\"availability\":\"AVAILABLE\"", "\"availability\":\"SOMETIMES\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.availability").value("invalid value 'SOMETIMES'"));
        create(auth, ProfileJson.withServices("Validation Workshop", "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.services").exists());
        create(auth, ProfileJson.withServices("Validation Workshop",
                "[{\"name\":\"Free repair\",\"category\":\"MOBILE\",\"priceType\":\"FIXED\",\"price\":0}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['services[0].price']").exists());
        create(auth, ProfileJson.withServices("Validation Workshop",
                "[{\"name\":\"Precise repair\",\"category\":\"MOBILE\",\"priceType\":\"FIXED\",\"price\":10.555}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['services[0].price']").exists());
        create(auth, ProfileJson.replace(valid, "{\"day\":\"MONDAY\",\"opens\":\"10:00\"", "{\"day\":\"MONDAY\",\"opens\":\"25:00\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['workingHours[0].opens']").exists());
        create(auth, "{not json").andExpect(status().isBadRequest());
    }

    @Test
    void crossFieldRulesAre400() throws Exception {
        String auth = TestTokens.repairer(ACCOUNT_IDS.incrementAndGet());
        String valid = ProfileJson.valid("Rules Workshop");

        // swapped coordinates (lng, lat) are called out
        String swapped = ProfileJson.replace(valid, "\"latitude\":23.0276", "\"latitude\":72.5577");
        swapped = ProfileJson.replace(swapped, "\"longitude\":72.5577", "\"longitude\":23.0276");
        create(auth, swapped).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.latitude", containsString("swapped")));
        // outside India (London)
        String london = ProfileJson.replace(valid, "\"latitude\":23.0276", "\"latitude\":51.5072");
        london = ProfileJson.replace(london, "\"longitude\":72.5577", "\"longitude\":-0.1276");
        create(auth, london).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.latitude", containsString("India")));
        // service category not offered by the workshop
        create(auth, ProfileJson.withServices("Rules Workshop",
                "[{\"name\":\"Cycle service\",\"category\":\"BICYCLE\",\"priceType\":\"FIXED\",\"price\":300}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['services[0].category']").exists());
        // duplicate service names
        create(auth, ProfileJson.withServices("Rules Workshop", """
                [{"name":"Screen","category":"MOBILE","priceType":"FIXED","price":300},
                 {"name":"screen ","category":"MOBILE","priceType":"FIXED","price":400}]"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['services[1].name']").value("duplicate service name"));
        // service ids can't be sent on create
        create(auth, ProfileJson.withServices("Rules Workshop",
                "[{\"id\":5,\"name\":\"Screen\",\"category\":\"MOBILE\",\"priceType\":\"FIXED\",\"price\":300}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['services[0].id']").exists());
        // closing before opening, duplicate day
        String badHours = ProfileJson.replace(valid, "{\"day\":\"MONDAY\",\"opens\":\"10:00\",\"closes\":\"19:00\"}",
                "{\"day\":\"MONDAY\",\"opens\":\"19:00\",\"closes\":\"10:00\"}");
        create(auth, badHours).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['workingHours[0].closes']").exists());
        String dupDay = ProfileJson.replace(valid, "{\"day\":\"TUESDAY\"", "{\"day\":\"MONDAY\"");
        create(auth, dupDay).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['workingHours[1].day']").exists());

        // nothing was created by the rejected requests
        mvc.perform(get("/api/repairers/me").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isNotFound());
    }

    @Test
    void profileCreatedUnderAnIdIsReachableViaTheLocationHeader() throws Exception {
        long account = ACCOUNT_IDS.incrementAndGet();
        String location = create(TestTokens.repairer(account), ProfileJson.valid("Location Workshop"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        mvc.perform(get(location)).andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Location Workshop"));
    }
}
