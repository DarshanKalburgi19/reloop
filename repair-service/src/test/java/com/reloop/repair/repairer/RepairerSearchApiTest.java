package com.reloop.repair.repairer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.reloop.repair.repository.RepairerProfileRepository;
import com.reloop.repair.seed.DemoRepairerSeeder;
import com.reloop.repair.seed.DemoRepairers;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Customer search and /nearby against the seeded Gujarat demo workshops. Read-only: the data comes from
 * {@link DemoRepairerSeeder} (enabled here) in this class's own in-memory database.
 */
@SpringBootTest(properties = {
        "reloop.seed.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:repairer-search-test;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class RepairerSearchApiTest {

    /** CG Road, Ahmedabad. */
    private static final String CG_ROAD_LAT = "23.0225";
    private static final String CG_ROAD_LNG = "72.5714";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RepairerProfileRepository profiles;

    @Autowired
    private DemoRepairerSeeder seeder;

    /* ─────────────────────────────── seeding ─────────────────────────────── */

    @Test
    void seederCreatesLabelledDemoWorkshopsOnceWithRatings() {
        assertThat(profiles.findByDemoTrue()).hasSize(DemoRepairers.WORKSHOPS.size());
        seeder.run(null); // idempotent
        assertThat(profiles.findByDemoTrue()).hasSize(DemoRepairers.WORKSHOPS.size());
        assertThat(profiles.findByDemoTrue()).allSatisfy(p -> {
            assertThat(p.getAccountExternalId()).isNegative();
            assertThat(p.getBusinessName()).endsWith("(Demo)");
            assertThat(p.isProfileComplete()).isTrue();
            // every demo workshop is in Gujarat
            assertThat(p.getLatitude()).isBetween(20.0, 24.8);
            assertThat(p.getLongitude()).isBetween(68.0, 74.6);
        });
    }

    @Test
    void searchResultsCarryTheDemoLabel() throws Exception {
        mvc.perform(get("/api/repairers").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(DemoRepairers.WORKSHOPS.size()))
                .andExpect(jsonPath("$.content[*].demo", everyItem(org.hamcrest.Matchers.is(true))))
                .andExpect(jsonPath("$.content[0].demoNotice").exists())
                .andExpect(jsonPath("$.content[0].location.lat").isNumber())
                .andExpect(jsonPath("$.content[0].distanceKm").doesNotExist());
    }

    /* ─────────────────────────────── filters ─────────────────────────────── */

    @Test
    void cityFilterIsCaseInsensitive() throws Exception {
        mvc.perform(get("/api/repairers").param("city", "ahmedabad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].city", everyItem(org.hamcrest.Matchers.is("Ahmedabad"))));
        mvc.perform(get("/api/repairers").param("city", "Nowhere"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void categoryFilter() throws Exception {
        mvc.perform(get("/api/repairers").param("city", "Surat").param("category", "BICYCLE"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].businessName").value("Varachha Cycle Works (Demo)"))
                .andExpect(jsonPath("$.content[0].categories", hasItem("BICYCLE")));
    }

    @Test
    void priceFilterNeedsOneServiceInRangeInTheChosenCategory() throws Exception {
        String json = mvc.perform(get("/api/repairers").param("category", "LAPTOP").param("maxPrice", "1000"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> names = JsonPath.read(json, "$.content[*].businessName");
        assertThat(names).containsExactlyInAnyOrder("Maninagar Laptop Clinic (Demo)", "Kalawad Road Mobile Hub (Demo)");
        // the card's starting price is the cheapest matching service
        List<String> labels = JsonPath.read(json, "$.content[*].startingPriceLabel");
        assertThat(labels).containsOnly("From ₹499");

        mvc.perform(get("/api/repairers").param("minPrice", "2000").param("maxPrice", "2600").param("category", "TV"))
                .andExpect(jsonPath("$.content[*].businessName", containsInAnyOrder("Vastrapur Home Appliance Repairs (Demo)")));
    }

    @Test
    void methodAndAvailabilityFilters() throws Exception {
        String json = mvc.perform(get("/api/repairers").param("method", "SEND_ITEM").param("availableOnly", "true"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> names = JsonPath.read(json, "$.content[*].businessName");
        assertThat(names).hasSize(4).doesNotContain("Fatehgunj Fix Point (Demo)");
        List<List<String>> methods = JsonPath.read(json, "$.content[*].repairMethods");
        assertThat(methods).allSatisfy(m -> assertThat(m).contains("SEND_ITEM"));

        mvc.perform(get("/api/repairers").param("method", "SEND_ITEM"))
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    void keywordSearchMatchesNameDescriptionAndExpertise() throws Exception {
        mvc.perform(get("/api/repairers").param("q", "iphone"))
                .andExpect(jsonPath("$.content[*].businessName", hasItem("Adajan Smart Repairs (Demo)")));
        mvc.perform(get("/api/repairers").param("q", "e-cycles"))
                .andExpect(jsonPath("$.content[*].businessName", containsInAnyOrder("Varachha Cycle Works (Demo)")));
    }

    /* ──────────────────────────── sort and paging ──────────────────────────── */

    @Test
    void sortsByRatingPriceAndName() throws Exception {
        // QuickFix: 5,5,4,5 = 4.75 is the best-rated demo workshop
        mvc.perform(get("/api/repairers"))
                .andExpect(jsonPath("$.content[0].businessName").value("QuickFix Mobile Care (Demo)"))
                .andExpect(jsonPath("$.content[0].rating.average").value(4.8))
                .andExpect(jsonPath("$.content[0].rating.count").value(4));
        mvc.perform(get("/api/repairers").param("sort", "price_asc"))
                .andExpect(jsonPath("$.content[0].businessName").value("Varachha Cycle Works (Demo)"))
                .andExpect(jsonPath("$.content[0].startingPriceLabel").value("From ₹80"));
        mvc.perform(get("/api/repairers").param("sort", "name").param("size", "2").param("page", "0"))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].businessName").value("Adajan Smart Repairs (Demo)"))
                .andExpect(jsonPath("$.totalPages").value(5))
                .andExpect(jsonPath("$.first").value(true));
    }

    @Test
    void invalidSearchParametersAre400() throws Exception {
        mvc.perform(get("/api/repairers").param("minPrice", "5000").param("maxPrice", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.priceRangeValid").exists());
        mvc.perform(get("/api/repairers").param("category", "SPACESHIP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.category", containsString("SPACESHIP")));
        mvc.perform(get("/api/repairers").param("sort", "cheapest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sort").exists());
        mvc.perform(get("/api/repairers").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.size").exists());
    }

    /* ─────────────────────────────── details ─────────────────────────────── */

    @Test
    void publicDetailShowsLocationMethodsServicesHoursAndRatings() throws Exception {
        long id = profiles.findByDemoTrue().stream()
                .filter(p -> p.getBusinessName().startsWith("Adajan")).findFirst().orElseThrow().getId();
        mvc.perform(get("/api/repairers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Surat"))
                .andExpect(jsonPath("$.latitude").value(21.194))
                .andExpect(jsonPath("$.longitude").value(72.7932))
                .andExpect(jsonPath("$.repairMethods", hasSize(3)))
                .andExpect(jsonPath("$.services", hasSize(5)))
                .andExpect(jsonPath("$.services[0].priceLabel").exists())
                .andExpect(jsonPath("$.workingHoursSummary").value("Mon-Sat 10:00-21:00, Sun closed"))
                .andExpect(jsonPath("$.workingHours", hasSize(6)))
                .andExpect(jsonPath("$.rating.average").value(4.6))
                .andExpect(jsonPath("$.rating.count").value(5))
                .andExpect(jsonPath("$.recentReviews", hasSize(5)))
                .andExpect(jsonPath("$.recentReviews[0].demo").value(true))
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.demoNotice").exists());
    }

    @Test
    void workshopWithoutReviewsHasNoAverage() throws Exception {
        mvc.perform(get("/api/repairers").param("city", "Bhavnagar"))
                .andExpect(jsonPath("$.content[0].rating.count").value(0))
                .andExpect(jsonPath("$.content[0].rating.average").doesNotExist());
    }

    /* ─────────────────────────────── nearby ─────────────────────────────── */

    @Test
    void nearbyReturnsWorkshopsWithinTheRadiusNearestFirst() throws Exception {
        String json = mvc.perform(get("/api/repairers/nearby").param("lat", CG_ROAD_LAT).param("lng", CG_ROAD_LNG)
                        .param("radiusKm", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.center.lat").value(23.0225))
                .andExpect(jsonPath("$.radiusKm").value(10.0))
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.results[0].businessName").value("QuickFix Mobile Care (Demo)"))
                .andExpect(jsonPath("$.results[*].distanceKm", everyItem(lessThanOrEqualTo(10.0))))
                .andExpect(jsonPath("$.bounds.southWest.lat").isNumber())
                .andExpect(jsonPath("$.bounds.northEast.lng").isNumber())
                .andReturn().getResponse().getContentAsString();
        List<Double> distances = JsonPath.read(json, "$.results[*].distanceKm");
        assertThat(distances).isSorted();
        double north = JsonPath.read(json, "$.bounds.northEast.lat");
        double south = JsonPath.read(json, "$.bounds.southWest.lat");
        assertThat(north - south).isBetween(0.17, 0.19); // ~2 x 10 km of latitude

        // a wider radius reaches Gandhinagar (~25 km) but not Surat (~205 km)
        mvc.perform(get("/api/repairers/nearby").param("lat", CG_ROAD_LAT).param("lng", CG_ROAD_LNG).param("radiusKm", "30"))
                .andExpect(jsonPath("$.count").value(4))
                .andExpect(jsonPath("$.results[*].businessName", hasItem("Sector 21 Gadget Doctor (Demo)")))
                .andExpect(jsonPath("$.results[*].city", not(hasItem("Surat"))));
    }

    @Test
    void nearbyAppliesFiltersAndLimit() throws Exception {
        mvc.perform(get("/api/repairers/nearby").param("lat", CG_ROAD_LAT).param("lng", CG_ROAD_LNG)
                        .param("radiusKm", "10").param("category", "HOME_APPLIANCE"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].businessName").value("Vastrapur Home Appliance Repairs (Demo)"));
        mvc.perform(get("/api/repairers/nearby").param("lat", CG_ROAD_LAT).param("lng", CG_ROAD_LNG)
                        .param("radiusKm", "10").param("availableOnly", "true"))
                .andExpect(jsonPath("$.count").value(2)); // Vastrapur is BUSY
        mvc.perform(get("/api/repairers/nearby").param("lat", CG_ROAD_LAT).param("lng", CG_ROAD_LNG)
                        .param("radiusKm", "100").param("limit", "2"))
                .andExpect(jsonPath("$.results", hasSize(2)));
    }

    @Test
    void nearbyValidatesCoordinatesAndRadius() throws Exception {
        mvc.perform(get("/api/repairers/nearby").param("lng", CG_ROAD_LNG))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.lat").exists());
        mvc.perform(get("/api/repairers/nearby").param("lat", "123").param("lng", CG_ROAD_LNG))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.lat").exists());
        mvc.perform(get("/api/repairers/nearby").param("lat", CG_ROAD_LAT).param("lng", CG_ROAD_LNG).param("radiusKm", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.radiusKm").exists());
        mvc.perform(get("/api/repairers/nearby").param("lat", "abc").param("lng", CG_ROAD_LNG))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.lat", containsString("abc")));
    }
}
