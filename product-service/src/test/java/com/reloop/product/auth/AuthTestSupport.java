package com.reloop.product.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.reloop.product.client.CreateRepairerProfileCommand;
import com.reloop.product.client.RepairerProfileView;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** JSON fixtures and helpers shared by the auth integration tests. */
final class AuthTestSupport {

    /**
     * These tests commit real rows (registration is not rolled back), so they use their own
     * in-memory database. Otherwise count-based assertions in other test classes sharing
     * jdbc:h2:mem:product-test-db (e.g. AccountRepositoryTest) could see extra accounts.
     */
    static final String ISOLATED_DB = "spring.datasource.url=jdbc:h2:mem:product-auth-test;DB_CLOSE_DELAY=-1";

    static final String CUSTOMER_PASSWORD = "Customer1pass";
    static final String REPAIRER_PASSWORD = "Repairer1pass";

    private static final AtomicLong PROFILE_IDS = new AtomicLong(1000);

    private AuthTestSupport() {
    }

    static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    static String customerJson(String email) {
        return """
            {"name":"Asha Patel","email":"%s","phone":"+919812345678","password":"%s",
             "address":"12 Riverfront Road","city":"Ahmedabad","pincode":"380009","state":"Gujarat"}
            """.formatted(email, CUSTOMER_PASSWORD);
    }

    static String repairerJson(String email) {
        return repairerJson(email, "[\"IN_PERSON\",\"SEND_ITEM\"]", 23.0225);
    }

    static String repairerJson(String email, String methodsJson, double latitude) {
        return """
            {"name":"Ravi Mehta","email":"%s","phone":"9898989898","password":"%s",
             "workshopName":"Mehta Fix-It","repairExpertise":["Mobile phones","Laptops"],
             "workshopAddress":"5 CG Road","city":"Ahmedabad","pincode":"380006",
             "latitude":%s,"longitude":72.5714,"serviceHours":"Mon-Sat 10:00-19:00",
             "repairMethods":%s,"verificationStatus":"VERIFIED"}
            """.formatted(email, REPAIRER_PASSWORD, latitude, methodsJson);
    }

    static String loginJson(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    /** What a healthy repair-service would answer for the given command. */
    static RepairerProfileView profileFor(CreateRepairerProfileCommand c) {
        return new RepairerProfileView(PROFILE_IDS.incrementAndGet(), c.accountExternalId(), c.businessName(),
                c.specialties(), c.phone(), c.addressLine(), c.city(), c.pincode(), c.latitude(), c.longitude(),
                c.serviceHours(), c.acceptsInPerson(), c.acceptsPickupDrop(), c.acceptsShipping(),
                "UNVERIFIED", "ACTIVE");
    }

    static MvcResult postJson(MockMvc mvc, String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    static String token(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }
}
