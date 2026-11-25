package com.reloop.product.marketplace;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Helpers shared by the marketplace integration tests. */
final class MarketplaceTestSupport {

    static final ObjectMapper JSON = new ObjectMapper();

    private MarketplaceTestSupport() {
    }

    /** A registered customer: account id, bearer token and email. */
    record Customer(long id, String token, String email) {
        String bearer() {
            return "Bearer " + token;
        }
    }

    static Customer registerCustomer(MockMvc mvc, String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String body = """
            {"name":"%s Tester","email":"%s","phone":"9876543210","password":"Market1pass",
             "address":"1 Test Road","city":"Ahmedabad","pincode":"380001","state":"Gujarat"}
            """.formatted(prefix, email);
        MvcResult result = mvc.perform(post("/api/auth/register/customer")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new IllegalStateException("registration failed: " + result.getResponse().getContentAsString());
        }
        String json = result.getResponse().getContentAsString();
        Number id = JsonPath.read(json, "$.account.id");
        return new Customer(id.longValue(), JsonPath.read(json, "$.accessToken"), email);
    }

    /** A valid listing body; override any field via {@code overrides} (null value = remove field). */
    static String listingJson(Map<String, Object> overrides) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Used Sony headphones");
        body.put("description", "Good sound, light scratches on the headband.");
        body.put("category", "ELECTRONICS");
        body.put("price", 2499.50);
        body.put("condition", "GOOD");
        body.put("city", "Surat");
        body.put("pickupLocation", "Adajan, near Star Bazaar");
        overrides.forEach((k, v) -> {
            if (v == null) {
                body.remove(k);
            } else {
                body.put(k, v);
            }
        });
        return JSON.writeValueAsString(body);
    }

    static String listingJson() throws Exception {
        return listingJson(Map.of());
    }

    static MockHttpServletRequestBuilder jsonPost(String url, String json, Customer as) {
        MockHttpServletRequestBuilder b = post(url).contentType(MediaType.APPLICATION_JSON).content(json);
        return as == null ? b : b.header("Authorization", as.bearer());
    }

    /** Creates a listing and returns its id. */
    static long createListing(MockMvc mvc, Customer seller, Map<String, Object> overrides) throws Exception {
        MvcResult result = mvc.perform(jsonPost("/api/products", listingJson(overrides), seller)).andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new IllegalStateException("create failed: " + result.getResponse().getContentAsString());
        }
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    static <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    static List<String> eventTypes(MvcResult passport) throws Exception {
        return read(passport, "$.history[*].eventType");
    }
}
