package com.reloop.repair.repairrequest;

import static com.reloop.repair.repairrequest.RepairFixtures.customer;
import static com.reloop.repair.repairrequest.RepairFixtures.repairer;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.reloop.repair.client.OrderServiceClient;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Who may see and change a repair request: customers only their own, repairers only requests assigned to their
 * workshop (others get 404 so ids don't leak), wrong roles get 403, anonymous 401, internal calls need the key.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:repair-access-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class RepairRequestAccessTest {

    private static final AtomicLong ACCOUNTS = new AtomicLong(8000);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrderServiceClient orderService;

    private long owner;
    private long assignedRepairer;
    private long requestId;

    @BeforeEach
    void setUp() throws Exception {
        owner = ACCOUNTS.incrementAndGet();
        assignedRepairer = ACCOUNTS.incrementAndGet();
        long profileId = workshop(assignedRepairer);
        String json = mvc.perform(post("/api/repair-requests").header(HttpHeaders.AUTHORIZATION, customer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RepairFixtures.requestBody(profileId, "LAPTOP", "IN_PERSON")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        requestId = ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private long workshop(long account) throws Exception {
        String json = mvc.perform(post("/api/repairers").header(HttpHeaders.AUTHORIZATION, repairer(account))
                        .contentType(MediaType.APPLICATION_JSON).content(RepairFixtures.workshop("Access Lab " + account)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void ownerAndAssignedRepairerCanView() throws Exception {
        mvc.perform(get("/api/repair-requests/{id}", requestId).header(HttpHeaders.AUTHORIZATION, customer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryMethod").value("IN_PERSON"));
        mvc.perform(get("/api/repair-requests/{id}", requestId).header(HttpHeaders.AUTHORIZATION, repairer(assignedRepairer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedActions", hasItem("accept")));
        mvc.perform(get("/api/repair-requests/{id}/history", requestId).header(HttpHeaders.AUTHORIZATION, repairer(assignedRepairer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].toStatus").value("REQUESTED"));
    }

    @Test
    void otherCustomersCannotSeeOrDecide() throws Exception {
        String stranger = customer(ACCOUNTS.incrementAndGet());
        mvc.perform(get("/api/repair-requests/{id}", requestId).header(HttpHeaders.AUTHORIZATION, stranger))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/repair-requests/{id}/history", requestId).header(HttpHeaders.AUTHORIZATION, stranger))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/repair-requests/{id}/quote/approve", requestId).header(HttpHeaders.AUTHORIZATION, stranger))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/repair-requests/{id}/cancel", requestId).header(HttpHeaders.AUTHORIZATION, stranger)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"not mine\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/repair-requests/mine").header(HttpHeaders.AUTHORIZATION, stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem((int) requestId))));
        verify(orderService, never()).approveQuote(any(), any());
    }

    @Test
    void otherRepairersCannotSeeOrUpdate() throws Exception {
        long other = ACCOUNTS.incrementAndGet();
        workshop(other);
        String auth = repairer(other);

        mvc.perform(get("/api/repair-requests/{id}", requestId).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/repair-requests/{id}/accept", requestId).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/repair-requests/{id}/status", requestId).header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/repair-requests/incoming").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem((int) requestId))));

        // a repairer account without a workshop profile
        String noProfile = repairer(ACCOUNTS.incrementAndGet());
        mvc.perform(post("/api/repair-requests/{id}/accept", requestId).header(HttpHeaders.AUTHORIZATION, noProfile))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/repair-requests/{id}", requestId).header(HttpHeaders.AUTHORIZATION, customer(owner)))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void wrongRolesAre403() throws Exception {
        mvc.perform(post("/api/repair-requests").header(HttpHeaders.AUTHORIZATION, repairer(assignedRepairer))
                        .contentType(MediaType.APPLICATION_JSON).content(RepairFixtures.requestBody(1, "LAPTOP", "IN_PERSON")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/repair-requests/{id}/accept", requestId).header(HttpHeaders.AUTHORIZATION, customer(owner)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/repair-requests/{id}/quote/approve", requestId)
                        .header(HttpHeaders.AUTHORIZATION, repairer(assignedRepairer)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/repair-requests/incoming").header(HttpHeaders.AUTHORIZATION, customer(owner)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/repair-requests/mine").header(HttpHeaders.AUTHORIZATION, repairer(assignedRepairer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousIs401() throws Exception {
        mvc.perform(get("/api/repair-requests/{id}", requestId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/repair-requests/mine")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/repair-requests").contentType(MediaType.APPLICATION_JSON)
                        .content(RepairFixtures.requestBody(1, "LAPTOP", "IN_PERSON")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void paymentConfirmationNeedsTheInternalKey() throws Exception {
        String body = "{\"quoteId\":1,\"paymentTransactionId\":1,\"transactionId\":\"PAY-X\",\"amountPaise\":100}";
        mvc.perform(post("/api/internal/repair-requests/{id}/payment-confirmation", requestId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/internal/repair-requests/{id}/payment-confirmation", requestId)
                        .header("X-Internal-Api-Key", "wrong").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        // a customer's JWT is no substitute for the service key
        mvc.perform(post("/api/internal/repair-requests/{id}/payment-confirmation", requestId)
                        .header(HttpHeaders.AUTHORIZATION, customer(owner)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void paymentConfirmationIsValidatedAfterTheKey() throws Exception {
        mvc.perform(post("/api/internal/repair-requests/{id}/payment-confirmation", requestId)
                        .header("X-Internal-Api-Key", RepairFixtures.INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quoteId\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/internal/repair-requests/{id}/payment-confirmation", 999_999)
                        .header("X-Internal-Api-Key", RepairFixtures.INTERNAL_KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quoteId\":1,\"paymentTransactionId\":1,\"transactionId\":\"PAY-X\",\"amountPaise\":100}"))
                .andExpect(status().isNotFound());
    }
}
