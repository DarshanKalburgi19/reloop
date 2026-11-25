package com.reloop.repair.repairrequest;

import static com.reloop.repair.repairrequest.RepairFixtures.INTERNAL_KEY;
import static com.reloop.repair.repairrequest.RepairFixtures.customer;
import static com.reloop.repair.repairrequest.RepairFixtures.repairer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.reloop.repair.client.CreateQuoteCommand;
import com.reloop.repair.client.OrderServiceClient;
import com.reloop.repair.client.QuoteDecisionCommand;
import com.reloop.repair.client.QuoteView;
import com.reloop.repair.exception.OrderServiceException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * End-to-end repair booking over HTTP: customer submits, the assigned repairer accepts, inspects and quotes,
 * the customer approves or rejects, order-service (mocked) records quotes and confirms payment, the repairer
 * works and completes. Also covers who may see/change what and the workflow rules.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:repair-flow-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class RepairRequestFlowTest {

    private static final AtomicLong ACCOUNTS = new AtomicLong(7000);

    @Autowired
    private MockMvc mvc;

    /** Stands in for order-service; the fake below behaves like the real quote API. */
    @MockitoBean
    private OrderServiceClient orderService;

    private final AtomicLong quoteIds = new AtomicLong(900);
    private final Map<Long, QuoteView> quotes = new ConcurrentHashMap<>();
    private final Map<Long, Integer> revisions = new ConcurrentHashMap<>();

    private long repairerAccount;
    private long profileId;
    private long customerAccount;

    @BeforeEach
    void setUp() throws Exception {
        when(orderService.createQuote(any())).thenAnswer(inv -> {
            CreateQuoteCommand cmd = inv.getArgument(0);
            long id = quoteIds.incrementAndGet();
            QuoteView q = RepairFixtures.quote(id, cmd, revisions.merge(cmd.repairRequestId(), 1, Integer::sum));
            quotes.put(id, q);
            return q;
        });
        when(orderService.approveQuote(anyLong(), any())).thenAnswer(inv ->
                RepairFixtures.withStatus(quotes.get(inv.<Long>getArgument(0)), inv.<Long>getArgument(0), "APPROVED"));
        when(orderService.rejectQuote(anyLong(), any())).thenAnswer(inv ->
                RepairFixtures.withStatus(quotes.get(inv.<Long>getArgument(0)), inv.<Long>getArgument(0), "REJECTED"));

        repairerAccount = ACCOUNTS.incrementAndGet();
        customerAccount = ACCOUNTS.incrementAndGet();
        profileId = workshopFor(repairerAccount, "Flow Test Lab " + repairerAccount);
    }

    /* ───────────────────────────── helpers ───────────────────────────── */

    private long workshopFor(long account, String name) throws Exception {
        String json = mvc.perform(post("/api/repairers").header(HttpHeaders.AUTHORIZATION, repairer(account))
                        .contentType(MediaType.APPLICATION_JSON).content(RepairFixtures.workshop(name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private ResultActions submit(String auth, String body) throws Exception {
        return mvc.perform(post("/api/repair-requests").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long submitted() throws Exception {
        String json = submit(customer(customerAccount), RepairFixtures.requestBody(profileId, "MOBILE", "SEND_ITEM"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private ResultActions act(String auth, long id, String action, String json) throws Exception {
        var request = post("/api/repair-requests/{id}/" + action, id).header(HttpHeaders.AUTHORIZATION, auth);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(request);
    }

    private ResultActions asRepairer(long id, String action, String json) throws Exception {
        return act(repairer(repairerAccount), id, action, json);
    }

    private ResultActions asCustomer(long id, String action, String json) throws Exception {
        return act(customer(customerAccount), id, action, json);
    }

    private static String note(String text) {
        return "{\"note\":\"" + text + "\"}";
    }

    private static String quoteBody(String amount) {
        return "{\"amount\":" + amount + ",\"summary\":\"Replace OLED panel and adhesive\",\"estimatedDays\":2}";
    }

    private long inspected() throws Exception {
        long id = submitted();
        asRepairer(id, "accept", null).andExpect(status().isOk());
        asRepairer(id, "inspect", "{\"findings\":\"OLED panel cracked, frame intact\"}").andExpect(status().isOk());
        return id;
    }

    /** Request with a sent quote; returns {requestId, quoteId}. */
    private long[] quoted(String amount) throws Exception {
        long id = inspected();
        String json = asRepairer(id, "quote", quoteBody(amount)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new long[] {id, ((Number) JsonPath.read(json, "$.quote.quoteId")).longValue()};
    }

    private long latestQuoteId(long id) throws Exception {
        String json = mvc.perform(get("/api/repair-requests/{id}", id).header(HttpHeaders.AUTHORIZATION, customer(customerAccount)))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.quote.quoteId")).longValue();
    }

    private ResultActions confirmPayment(long id, long quoteId, long paymentId, long amountPaise) throws Exception {
        return mvc.perform(post("/api/internal/repair-requests/{id}/payment-confirmation", id)
                .header("X-Internal-Api-Key", INTERNAL_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quoteId\":%d,\"paymentTransactionId\":%d,\"transactionId\":\"PAY-20261125-TEST%06d\",\"amountPaise\":%d}"
                        .formatted(quoteId, paymentId, paymentId, amountPaise)));
    }

    /* ───────────────────────────── happy path ───────────────────────────── */

    @Test
    void completeRepairJourneyWithTimestampedHistory() throws Exception {
        String created = submit(customer(customerAccount), RepairFixtures.requestBody(profileId, "MOBILE", "SEND_ITEM"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/repair-requests/")))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.requestNumber", matchesPattern("RR-\\d{8}-[0-9A-HJKMNP-TV-Z]{8}")))
                .andExpect(jsonPath("$.customerAccountId").value(customerAccount))
                .andExpect(jsonPath("$.repairer.profileId").value(profileId))
                .andExpect(jsonPath("$.category").value("MOBILE"))
                .andExpect(jsonPath("$.deliveryMethod").value("SEND_ITEM"))
                .andExpect(jsonPath("$.deliveryInstructions", notNullValue()))
                .andExpect(jsonPath("$.imageUrls[0]").value("/demo-images/repairs/phone-cracked-screen.svg"))
                .andExpect(jsonPath("$.allowedActions", contains("cancel")))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();

        mvc.perform(get("/api/repair-requests/incoming").header(HttpHeaders.AUTHORIZATION, repairer(repairerAccount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem((int) id)));

        asRepairer(id, "accept", note("Bring it in any weekday")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.allowedActions", containsInAnyOrder("inspect", "cancel")));
        asRepairer(id, "inspect", "{\"findings\":\"OLED panel cracked, frame intact\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INSPECTED"))
                .andExpect(jsonPath("$.inspectionNotes").value("OLED panel cracked, frame intact"));
        asRepairer(id, "quote", quoteBody("1500.00")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUOTED"))
                .andExpect(jsonPath("$.quote.revision").value(1))
                .andExpect(jsonPath("$.quote.repairAmount").value(1500.00))
                .andExpect(jsonPath("$.quote.customerFee").value(30.00))
                .andExpect(jsonPath("$.quote.totalPayable").value(1530.00))
                .andExpect(jsonPath("$.quote.estimatedDays").value(2));
        verify(orderService).createQuote(new CreateQuoteCommand(id, JsonPath.read(created, "$.requestNumber"), profileId,
                customerAccount, 150_000L, "Replace OLED panel and adhesive"));

        long quoteId = latestQuoteId(id);
        asCustomer(id, "quote/approve", note("Go ahead")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.approvedAt", notNullValue()))
                .andExpect(jsonPath("$.allowedActions", containsInAnyOrder("pay", "cancel")));
        verify(orderService).approveQuote(eq(quoteId), eq(new QuoteDecisionCommand(id, customerAccount, "Go ahead")));

        confirmPayment(id, quoteId, 41, 153_000).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paymentTransactionId").value(41));

        asRepairer(id, "progress", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        asRepairer(id, "progress", note("New panel fitted, testing touch")).andExpect(status().isOk());
        asRepairer(id, "complete", note("Ready for dispatch")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt", notNullValue()))
                .andExpect(jsonPath("$.allowedActions", hasSize(0)));

        mvc.perform(get("/api/repair-requests/{id}/history", id).header(HttpHeaders.AUTHORIZATION, customer(customerAccount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].toStatus", contains("REQUESTED", "ACCEPTED", "INSPECTED", "QUOTED",
                        "QUOTE_APPROVED", "AWAITING_PAYMENT", "PAID", "IN_PROGRESS", "IN_PROGRESS", "COMPLETED")))
                .andExpect(jsonPath("$[0].fromStatus").doesNotExist())
                .andExpect(jsonPath("$[0].actor").value("CUSTOMER"))
                .andExpect(jsonPath("$[5].actor").value("SYSTEM"))
                .andExpect(jsonPath("$[6].note", containsString("PAY-20261125-TEST000041")))
                .andExpect(jsonPath("$[8].note", containsString("New panel fitted")))
                .andExpect(jsonPath("$[0].at", notNullValue()))
                .andExpect(jsonPath("$[9].at", notNullValue()));

        mvc.perform(get("/api/repair-requests/mine").header(HttpHeaders.AUTHORIZATION, customer(customerAccount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.content[0].quoteTotalPayable").value(1530.00));
    }

    /* ───────────────────────────── workflow rules ───────────────────────────── */

    @Test
    void rejectedQuoteCannotProceedToPaymentOrWork() throws Exception {
        long[] q = quoted("800");
        asCustomer(q[0], "quote/reject", note("Too expensive")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUOTE_REJECTED"));

        confirmPayment(q[0], q[1], 51, 81_600).andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("QUOTE_REJECTED"));
        asRepairer(q[0], "progress", null).andExpect(status().isConflict());
        asCustomer(q[0], "quote/approve", null).andExpect(status().isConflict());
    }

    @Test
    void revisedQuoteRequiresFreshApprovalBeforePayment() throws Exception {
        long[] q = quoted("1000");
        asCustomer(q[0], "quote/approve", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"));

        asRepairer(q[0], "quote/revise", quoteBody("1200.50")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUOTED"))
                .andExpect(jsonPath("$.quote.revision").value(2))
                .andExpect(jsonPath("$.approvedAt").doesNotExist())
                .andExpect(jsonPath("$.quote.totalPayable").value(1224.51));
        long revised = latestQuoteId(q[0]);
        assertThat(revised).isNotEqualTo(q[1]);

        // the old approval doesn't count, and the new quote isn't approved yet
        confirmPayment(q[0], q[1], 61, 102_000).andExpect(status().isConflict());
        confirmPayment(q[0], revised, 61, 122_451).andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("QUOTED"));

        asCustomer(q[0], "quote/approve", null).andExpect(status().isOk());
        confirmPayment(q[0], q[1], 61, 102_000).andExpect(status().isConflict()); // stale quote id
        confirmPayment(q[0], revised, 61, 122_451).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void onlyPaidJobsCanEnterInProgress() throws Exception {
        long[] q = quoted("500");
        asRepairer(q[0], "progress", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("QUOTED"))
                .andExpect(jsonPath("$.message", containsString("approved and paid")));
        mvc.perform(patch("/api/repair-requests/{id}/status", q[0]).header(HttpHeaders.AUTHORIZATION, repairer(repairerAccount))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isConflict());

        asCustomer(q[0], "quote/approve", null).andExpect(status().isOk());
        asRepairer(q[0], "progress", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("AWAITING_PAYMENT"));
        asRepairer(q[0], "complete", null).andExpect(status().isConflict());
    }

    @Test
    void paymentMustMatchTheApprovedTotalAndRepeatsAreNoOps() throws Exception {
        long[] q = quoted("1500");
        asCustomer(q[0], "quote/approve", null).andExpect(status().isOk());

        confirmPayment(q[0], q[1], 71, 150_000).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("doesn't match")));
        confirmPayment(q[0], q[1], 71, 153_000).andExpect(status().isOk());
        confirmPayment(q[0], q[1], 71, 153_000).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.history", hasSize(7)));
        confirmPayment(q[0], q[1], 72, 153_000).andExpect(status().isConflict()); // a second, different payment
    }

    @Test
    void notesAreRequiredForRejectCancelAndLaterProgress() throws Exception {
        long id = submitted();
        asRepairer(id, "reject", null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.note").exists());
        asCustomer(id, "cancel", note(" ")).andExpect(status().isBadRequest());

        asRepairer(id, "reject", note("We don't stock Galaxy panels")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.closeReason").value("We don't stock Galaxy panels"));
        asRepairer(id, "accept", null).andExpect(status().isConflict());
    }

    @Test
    void cancellationOnlyBeforePayment() throws Exception {
        long id = submitted();
        asCustomer(id, "cancel", note("Found a part locally")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.closedAt", notNullValue()));

        long[] q = quoted("400");
        asCustomer(q[0], "quote/approve", null).andExpect(status().isOk());
        confirmPayment(q[0], q[1], 81, 40_800).andExpect(status().isOk());
        asCustomer(q[0], "cancel", note("Changed my mind")).andExpect(status().isConflict());
        asRepairer(q[0], "cancel", note("Too busy")).andExpect(status().isConflict());
    }

    @Test
    void repairerCannotCancelANewRequestOnlyRejectIt() throws Exception {
        long id = submitted();
        asRepairer(id, "cancel", note("No")).andExpect(status().isConflict());
        asRepairer(id, "accept", null).andExpect(status().isOk());
        asRepairer(id, "cancel", note("Part unavailable")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void firstQuoteNeedsInspectionAndInspectionNeedsFindings() throws Exception {
        long id = submitted();
        asRepairer(id, "accept", null).andExpect(status().isOk());
        asRepairer(id, "quote", quoteBody("100")).andExpect(status().isConflict());
        asRepairer(id, "inspect", "{\"findings\":\"\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.findings").exists());
        verify(orderService, never()).createQuote(any());
    }

    @Test
    void patchStatusRejectsCustomerAndPaymentStatuses() throws Exception {
        long id = submitted();
        for (String s : List.of("QUOTE_APPROVED", "PAID", "QUOTED", "AWAITING_PAYMENT")) {
            mvc.perform(patch("/api/repair-requests/{id}/status", id).header(HttpHeaders.AUTHORIZATION, repairer(repairerAccount))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + s + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.status").exists());
        }
        mvc.perform(patch("/api/repair-requests/{id}/status", id).header(HttpHeaders.AUTHORIZATION, repairer(repairerAccount))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    /* ───────────────────────────── order-service failures ───────────────────────────── */

    @Test
    void orderServiceDownLeavesTheRequestUnchanged() throws Exception {
        long id = inspected();
        doThrow(new OrderServiceException(503, "Order service is unavailable", null)).when(orderService).createQuote(any());

        asRepairer(id, "quote", quoteBody("900")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/repair-requests/{id}", id).header(HttpHeaders.AUTHORIZATION, repairer(repairerAccount)))
                .andExpect(jsonPath("$.status").value("INSPECTED"))
                .andExpect(jsonPath("$.quote").doesNotExist());
    }

    @Test
    void orderServiceRefusingAnApprovalIsReported() throws Exception {
        long[] q = quoted("900");
        doThrow(new OrderServiceException(409, "Quote #1 expired; ask the repairer for a revised quote", null))
                .when(orderService).approveQuote(anyLong(), any());

        asCustomer(q[0], "quote/approve", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("expired")));
        mvc.perform(get("/api/repair-requests/{id}", q[0]).header(HttpHeaders.AUTHORIZATION, customer(customerAccount)))
                .andExpect(jsonPath("$.status").value("QUOTED"));
    }

    /* ───────────────────────────── validation ───────────────────────────── */

    @Test
    void submissionIsValidated() throws Exception {
        String auth = customer(customerAccount);
        submit(auth, RepairFixtures.requestBody(profileId, "MOBILE", "SEND_ITEM").replace(
                        "Screen cracked after a fall; touch stops working in the top half.", "broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.problemDescription").exists());
        submit(auth, RepairFixtures.requestBody(profileId, "MOBILE", "SEND_ITEM").replace(
                        "/demo-images/repairs/phone-cracked-screen.svg", "http://example.com/a.jpg"))
                .andExpect(status().isBadRequest());
        submit(auth, RepairFixtures.requestBody(profileId, "BICYCLE", "SEND_ITEM"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.category").exists());
        submit(auth, RepairFixtures.requestBody(profileId, "MOBILE", "PICKUP_DROPOFF"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.deliveryMethod").exists());
        submit(auth, RepairFixtures.requestBody(424242, "MOBILE", "IN_PERSON"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.repairerProfileId").exists());
        submit(auth, RepairFixtures.requestBody(profileId, "MOBILE", "TELEPORT"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void quoteAmountIsValidated() throws Exception {
        long id = inspected();
        asRepairer(id, "quote", quoteBody("0")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
        asRepairer(id, "quote", quoteBody("12.345")).andExpect(status().isBadRequest());
        verify(orderService, never()).createQuote(any());
    }

    @Test
    void demoImagesArePublic() throws Exception {
        mvc.perform(get("/api/repair-requests/demo-images"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem("/demo-images/repairs/phone-cracked-screen.svg")));
        mvc.perform(get("/demo-images/repairs/phone-cracked-screen.svg")).andExpect(status().isOk());
    }
}
