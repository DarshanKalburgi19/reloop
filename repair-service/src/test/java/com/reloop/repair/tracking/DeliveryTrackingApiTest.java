package com.reloop.repair.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.reloop.repair.client.MarketplaceOrderView;
import com.reloop.repair.client.OrderServiceClient;
import com.reloop.repair.entity.RepairRequest;
import com.reloop.repair.entity.RepairerProfile;
import com.reloop.repair.enums.DeliveryMode;
import com.reloop.repair.enums.RepairServiceCategory;
import com.reloop.repair.enums.RepairStatus;
import com.reloop.repair.exception.OrderServiceException;
import com.reloop.repair.repository.DeliveryLocationPointRepository;
import com.reloop.repair.repository.DeliveryRepository;
import com.reloop.repair.repository.RepairRequestRepository;
import com.reloop.repair.repository.RepairerProfileRepository;
import com.reloop.repair.service.DemoLocations;
import com.reloop.repair.service.GeoUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Simulated delivery tracking over HTTP: booking the three legs, the BOOKED → … → DELIVERED progression,
 * saved coordinate history, identical views for both parties, the SIMULATED labelling and authorization.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:tracking-api-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class DeliveryTrackingApiTest {

    private static final AtomicLong ACCOUNTS = new AtomicLong(8800);
    private static final AtomicLong NUMBERS = new AtomicLong(1);
    private static final AtomicLong ORDER_IDS = new AtomicLong(4400);
    private static final String TRACKING_PATTERN = "TRK-SIM-\\d{8}-[0-9A-HJKMNP-TV-Z]{8}";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RepairerProfileRepository profiles;
    @Autowired
    private RepairRequestRepository requests;
    @Autowired
    private DeliveryRepository deliveries;
    @Autowired
    private DeliveryLocationPointRepository points;

    /** Stands in for order-service (only getOrder is used by tracking). */
    @MockitoBean
    private OrderServiceClient orderService;

    private long customer;
    private long repairer;
    private long stranger;
    private RepairerProfile workshop;

    @BeforeEach
    void setUp() {
        customer = ACCOUNTS.incrementAndGet();
        repairer = ACCOUNTS.incrementAndGet();
        stranger = ACCOUNTS.incrementAndGet();
        workshop = workshopFor(repairer);
    }

    /* ───────────────────────────── helpers ───────────────────────────── */

    /** A workshop on CG Road, Ahmedabad (nearest demo location: AHD_CG_ROAD). */
    private RepairerProfile workshopFor(long account) {
        RepairerProfile p = new RepairerProfile();
        p.setAccountExternalId(account);
        p.setBusinessName("Tracking Lab " + account);
        p.setCity("Ahmedabad");
        p.setLatitude(23.0280);
        p.setLongitude(72.5580);
        p.setAcceptsInPerson(true);
        return profiles.saveAndFlush(p);
    }

    private RepairRequest repair(DeliveryMode mode, RepairStatus status) {
        RepairRequest r = new RepairRequest();
        r.setRequestNumber("RR-TRK-" + NUMBERS.getAndIncrement());
        r.setCustomerAccountId(customer);
        r.setRepairerProfile(workshop);
        r.setDeliveryMode(mode);
        r.setItemCategory(RepairServiceCategory.MOBILE);
        r.setItemDescription("Redmi Note 12");
        r.setStatus(status);
        return requests.saveAndFlush(r);
    }

    private void setStatus(RepairRequest r, RepairStatus status) {
        RepairRequest fresh = requests.findById(r.getId()).orElseThrow();
        fresh.setStatus(status);
        requests.saveAndFlush(fresh);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String auth, String json) throws Exception {
        if (auth != null) {
            request.header(HttpHeaders.AUTHORIZATION, auth);
        }
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(request);
    }

    private static String body(String type, Long repairId, Long orderId, String origin, String destination) {
        StringBuilder sb = new StringBuilder("{\"deliveryType\":\"").append(type).append('"');
        if (repairId != null) {
            sb.append(",\"repairRequestId\":").append(repairId);
        }
        if (orderId != null) {
            sb.append(",\"orderId\":").append(orderId);
        }
        if (origin != null) {
            sb.append(",\"originCode\":\"").append(origin).append('"');
        }
        if (destination != null) {
            sb.append(",\"destinationCode\":\"").append(destination).append('"');
        }
        return sb.append('}').toString();
    }

    private ResultActions book(String auth, String json) throws Exception {
        return send(post("/api/tracking/deliveries"), auth, json);
    }

    /** Customer books the inbound leg from Surat; returns the tracking number. */
    private String bookInbound(RepairRequest r) throws Exception {
        String json = book(TrackingTokens.customer(customer),
                body("CUSTOMER_TO_REPAIRER", r.getId(), null, "SURAT_ADAJAN", null))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.trackingNumber");
    }

    private ResultActions advance(String trackingNumber, String auth) throws Exception {
        return send(post("/api/tracking/deliveries/{t}/advance", trackingNumber), auth, null);
    }

    private void advanceTo(String trackingNumber, String auth, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            advance(trackingNumber, auth).andExpect(status().isOk());
        }
    }

    private String getJson(String trackingNumber, String auth) throws Exception {
        return send(get("/api/tracking/deliveries/{t}", trackingNumber), auth, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private MarketplaceOrderView order(long id, String status, long buyer, long seller) {
        MarketplaceOrderView view = new MarketplaceOrderView(id, "ORD-20261125-TEST" + id, status, buyer, seller, 42L,
                "Used Sony headphones", "CONFIRMED".equals(status) ? LocalDateTime.now() : null);
        when(orderService.getOrder(id)).thenReturn(view);
        return view;
    }

    private static double read(String json, String path) {
        return ((Number) JsonPath.read(json, path)).doubleValue();
    }

    /* ───────────────────────────── demo locations ───────────────────────────── */

    @Test
    void demoLocationsArePublicAndAllInGujarat() throws Exception {
        send(get("/api/tracking/demo-locations"), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(DemoLocations.ALL.size())))
                .andExpect(jsonPath("$[*].code", hasItem("AHD_CG_ROAD")))
                .andExpect(jsonPath("$[*].city", hasItem("Surat")));
    }

    /* ───────────────────────────── customer → repairer ───────────────────────────── */

    @Test
    void customerBooksTheInboundLegLabelledSimulatedWithTheWorkshopAsDefaultDestination() throws Exception {
        RepairRequest r = repair(DeliveryMode.SHIPPING, RepairStatus.ACCEPTED);

        String json = book(TrackingTokens.customer(customer), body("CUSTOMER_TO_REPAIRER", r.getId(), null, "surat_adajan", null))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/tracking/deliveries/TRK-SIM-")))
                .andExpect(jsonPath("$.simulated").value(true))
                .andExpect(jsonPath("$.simulationNotice", containsString("SIMULATED")))
                .andExpect(jsonPath("$.simulationNotice", containsString("No GPS device or courier")))
                .andExpect(jsonPath("$.carrier", containsString("simulated")))
                .andExpect(jsonPath("$.trackingNumber", matchesPattern(TRACKING_PATTERN)))
                .andExpect(jsonPath("$.deliveryType").value("CUSTOMER_TO_REPAIRER"))
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.statusFlow", hasSize(5)))
                .andExpect(jsonPath("$.nextStatus").value("PICKED_UP"))
                .andExpect(jsonPath("$.canAdvance").value(true))
                .andExpect(jsonPath("$.repairRequestId").value(r.getId()))
                .andExpect(jsonPath("$.orderId").value(nullValue()))
                .andExpect(jsonPath("$.referenceNumber").value(r.getRequestNumber()))
                .andExpect(jsonPath("$.customerAccountId").value(customer))
                .andExpect(jsonPath("$.partnerAccountId").value(repairer))
                .andExpect(jsonPath("$.origin.code").value("SURAT_ADAJAN"))
                .andExpect(jsonPath("$.origin.city").value("Surat"))
                .andExpect(jsonPath("$.destination.code").value("AHD_CG_ROAD"))
                .andExpect(jsonPath("$.progressPercent").value(0))
                .andExpect(jsonPath("$.route", hasSize(2)))
                .andExpect(jsonPath("$.history", hasSize(1)))
                .andExpect(jsonPath("$.history[0].sequence").value(0))
                .andExpect(jsonPath("$.history[0].status").value("BOOKED"))
                .andExpect(jsonPath("$.history[0].note", startsWith("Simulated booking")))
                .andReturn().getResponse().getContentAsString();

        // the marker starts at the origin
        assertThat(read(json, "$.currentPosition.latitude")).isEqualTo(read(json, "$.origin.latitude"));
        assertThat(read(json, "$.currentPosition.longitude")).isEqualTo(read(json, "$.origin.longitude"));
        assertThat(read(json, "$.routeDistanceKm")).isGreaterThan(200.0);
        assertThat(read(json, "$.remainingDistanceKm")).isEqualTo(read(json, "$.routeDistanceKm"));
        LocalDateTime booked = LocalDateTime.parse(JsonPath.read(json, "$.bookedAt"));
        LocalDateTime eta = LocalDateTime.parse(JsonPath.read(json, "$.estimatedArrivalAt"));
        assertThat(eta).isAfter(booked.plusHours(5));

        String trackingNumber = JsonPath.read(json, "$.trackingNumber");
        assertThat(deliveries.findByTrackingNumber(trackingNumber)).isPresent();
        assertThat(points.countByDelivery_Id(deliveries.findByTrackingNumber(trackingNumber).orElseThrow().getId()))
                .isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void advancingWalksEveryStatusMovesTowardTheDestinationAndSavesHistory() throws Exception {
        RepairRequest r = repair(DeliveryMode.PICKUP_DROP, RepairStatus.ACCEPTED);
        String t = bookInbound(r);

        List<String> statuses = new ArrayList<>();
        double previousRemaining = Double.MAX_VALUE;
        String json = null;
        for (int step = 1; step <= 7; step++) {
            String auth = step % 2 == 0 ? TrackingTokens.repairer(repairer) : TrackingTokens.customer(customer);
            json = advance(t, auth).andExpect(status().isOk())
                    .andExpect(jsonPath("$.simulated").value(true))
                    .andExpect(jsonPath("$.history", hasSize(step + 1)))
                    .andReturn().getResponse().getContentAsString();
            statuses.add(JsonPath.read(json, "$.status"));
            double remaining = GeoUtils.distanceKm(read(json, "$.currentPosition.latitude"),
                    read(json, "$.currentPosition.longitude"), read(json, "$.destination.latitude"),
                    read(json, "$.destination.longitude"));
            if (step >= 2) {
                assertThat(remaining).as("step %d", step).isLessThan(previousRemaining);
            }
            previousRemaining = remaining;
        }

        assertThat(statuses).containsExactly("PICKED_UP", "IN_TRANSIT", "IN_TRANSIT", "IN_TRANSIT", "IN_TRANSIT",
                "NEAR_DESTINATION", "DELIVERED");
        assertThat(read(json, "$.currentPosition.latitude")).isEqualTo(read(json, "$.destination.latitude"));
        assertThat(read(json, "$.currentPosition.longitude")).isEqualTo(read(json, "$.destination.longitude"));
        assertThat(read(json, "$.progressPercent")).isEqualTo(100);
        assertThat(read(json, "$.remainingDistanceKm")).isZero();
        assertThat((Object) JsonPath.read(json, "$.deliveredAt")).isNotNull();
        assertThat((Object) JsonPath.read(json, "$.pickedUpAt")).isNotNull();
        assertThat((Object) JsonPath.read(json, "$.nextStatus")).isNull();
        assertThat((Boolean) JsonPath.read(json, "$.canAdvance")).isFalse();
        assertThat((List<Integer>) JsonPath.read(json, "$.history[*].sequence")).containsExactly(0, 1, 2, 3, 4, 5, 6, 7);

        // advancing a delivered parcel is a conflict and saves nothing
        advance(t, TrackingTokens.customer(customer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("DELIVERED"))
                .andExpect(jsonPath("$.message", containsString("already DELIVERED")));
        long id = deliveries.findByTrackingNumber(t).orElseThrow().getId();
        assertThat(points.countByDelivery_Id(id)).isEqualTo(8);

        send(get("/api/tracking/deliveries/{t}/history", t), TrackingTokens.repairer(repairer), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[0].status").value("BOOKED"))
                .andExpect(jsonPath("$[1].status").value("PICKED_UP"))
                .andExpect(jsonPath("$[6].status").value("NEAR_DESTINATION"))
                .andExpect(jsonPath("$[7].status").value("DELIVERED"))
                .andExpect(jsonPath("$[7].progressPercent").value(100))
                .andExpect(jsonPath("$[7].note", startsWith("Simulated delivery")));
    }

    @Test
    void customerAndRepairerSeeExactlyTheSameTracking() throws Exception {
        RepairRequest r = repair(DeliveryMode.SHIPPING, RepairStatus.ACCEPTED);
        String t = bookInbound(r);
        advanceTo(t, TrackingTokens.customer(customer), 2);
        advanceTo(t, TrackingTokens.repairer(repairer), 1);

        String asCustomer = getJson(t, TrackingTokens.customer(customer));
        String asRepairer = getJson(t, TrackingTokens.repairer(repairer));

        assertThat(asRepairer).isEqualTo(asCustomer);
        assertThat((String) JsonPath.read(asCustomer, "$.status")).isEqualTo("IN_TRANSIT");
        assertThat((Integer) JsonPath.read(asCustomer, "$.progressPercent")).isEqualTo(40);

        String historyCustomer = send(get("/api/tracking/deliveries/{t}/history", t), TrackingTokens.customer(customer), null)
                .andReturn().getResponse().getContentAsString();
        String historyRepairer = send(get("/api/tracking/deliveries/{t}/history", t), TrackingTokens.repairer(repairer), null)
                .andReturn().getResponse().getContentAsString();
        assertThat(historyRepairer).isEqualTo(historyCustomer);

        // both see the leg in their lists and on the repair request
        send(get("/api/tracking/deliveries/mine"), TrackingTokens.customer(customer), null)
                .andExpect(jsonPath("$[*].trackingNumber", hasItem(t)));
        send(get("/api/tracking/deliveries/mine"), TrackingTokens.repairer(repairer), null)
                .andExpect(jsonPath("$[*].trackingNumber", hasItem(t)))
                .andExpect(jsonPath("$[0].simulated").value(true));
        send(get("/api/tracking/repair-requests/{id}/deliveries", r.getId()), TrackingTokens.repairer(repairer), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].trackingNumber").value(t))
                .andExpect(jsonPath("$[0].status").value("IN_TRANSIT"));
    }

    @Test
    void outsidersGet404AndAnonymousCallersGet401() throws Exception {
        RepairRequest r = repair(DeliveryMode.SHIPPING, RepairStatus.ACCEPTED);
        String t = bookInbound(r);
        long otherRepairer = ACCOUNTS.incrementAndGet();
        workshopFor(otherRepairer);

        for (String outsider : List.of(TrackingTokens.customer(stranger), TrackingTokens.repairer(otherRepairer))) {
            send(get("/api/tracking/deliveries/{t}", t), outsider, null).andExpect(status().isNotFound());
            send(get("/api/tracking/deliveries/{t}/history", t), outsider, null).andExpect(status().isNotFound());
            advance(t, outsider).andExpect(status().isNotFound());
            send(get("/api/tracking/repair-requests/{id}/deliveries", r.getId()), outsider, null)
                    .andExpect(status().isNotFound());
            send(get("/api/tracking/deliveries/mine"), outsider, null)
                    .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        }
        // an outsider cannot book a leg on someone else's repair either
        book(TrackingTokens.customer(stranger), body("CUSTOMER_TO_REPAIRER", r.getId(), null, "SURAT_ADAJAN", null))
                .andExpect(status().isNotFound());

        send(get("/api/tracking/deliveries/{t}", t), null, null).andExpect(status().isUnauthorized());
        advance(t, null).andExpect(status().isUnauthorized());
        book(null, body("CUSTOMER_TO_REPAIRER", r.getId(), null, "SURAT_ADAJAN", null))
                .andExpect(status().isUnauthorized());
        send(get("/api/tracking/deliveries/{t}", "TRK-SIM-20261125-NOSUCHXX"), TrackingTokens.customer(customer), null)
                .andExpect(status().isNotFound());

        // nothing moved
        assertThat(deliveries.findByTrackingNumber(t).orElseThrow().getSimulationStep()).isZero();
    }

    @Test
    void inboundLegNeedsAShippedAcceptedRequestAndCanOnlyBeBookedOnce() throws Exception {
        RepairRequest inPerson = repair(DeliveryMode.IN_PERSON, RepairStatus.ACCEPTED);
        book(TrackingTokens.customer(customer), body("CUSTOMER_TO_REPAIRER", inPerson.getId(), null, "SURAT_ADAJAN", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("IN_PERSON")));

        RepairRequest notAccepted = repair(DeliveryMode.SHIPPING, RepairStatus.REQUESTED);
        book(TrackingTokens.customer(customer), body("CUSTOMER_TO_REPAIRER", notAccepted.getId(), null, "SURAT_ADAJAN", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("REQUESTED"));

        RepairRequest ok = repair(DeliveryMode.SHIPPING, RepairStatus.ACCEPTED);
        // the repairer may book it too (e.g. arranging a pickup)
        String first = JsonPath.read(book(TrackingTokens.repairer(repairer),
                        body("CUSTOMER_TO_REPAIRER", ok.getId(), null, "AHD_MANINAGAR", null))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.trackingNumber");
        book(TrackingTokens.customer(customer), body("CUSTOMER_TO_REPAIRER", ok.getId(), null, "SURAT_ADAJAN", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString(first)));
    }

    @Test
    void bookingIsValidatedAgainstTheDemoLocations() throws Exception {
        RepairRequest r = repair(DeliveryMode.SHIPPING, RepairStatus.ACCEPTED);
        String auth = TrackingTokens.customer(customer);

        book(auth, body("CUSTOMER_TO_REPAIRER", r.getId(), null, "MUMBAI_BANDRA", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.originCode", containsString("unknown demo location")));
        book(auth, body("CUSTOMER_TO_REPAIRER", r.getId(), null, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.originCode", containsString("is required")));
        book(auth, body("CUSTOMER_TO_REPAIRER", r.getId(), null, "AHD_CG_ROAD", "AHD_CG_ROAD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.destinationCode", containsString("different")));
        book(auth, body("CUSTOMER_TO_REPAIRER", null, null, "SURAT_ADAJAN", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.repairRequestId").exists());
        book(auth, body("CUSTOMER_TO_REPAIRER", r.getId(), 5L, "SURAT_ADAJAN", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.orderId").exists());
        book(auth, "{\"repairRequestId\":" + r.getId() + ",\"originCode\":\"SURAT_ADAJAN\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.deliveryType").exists());
        book(auth, body("TELEPORT", r.getId(), null, "SURAT_ADAJAN", null))
                .andExpect(status().isBadRequest());

        assertThat(deliveries.findByRepairRequest_IdOrderByIdAsc(r.getId())).isEmpty();
    }

    /* ───────────────────────────── repairer → customer ───────────────────────────── */

    @Test
    void repairerBooksTheReturnLegBackToWhereTheItemCameFrom() throws Exception {
        RepairRequest r = repair(DeliveryMode.SHIPPING, RepairStatus.ACCEPTED);
        bookInbound(r);
        setStatus(r, RepairStatus.COMPLETED);

        book(TrackingTokens.customer(customer), body("REPAIRER_TO_CUSTOMER", r.getId(), null, null, null))
                .andExpect(status().isForbidden());

        String t = JsonPath.read(book(TrackingTokens.repairer(repairer), body("REPAIRER_TO_CUSTOMER", r.getId(), null, null, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deliveryType").value("REPAIRER_TO_CUSTOMER"))
                .andExpect(jsonPath("$.origin.code").value("AHD_CG_ROAD"))
                .andExpect(jsonPath("$.destination.code").value("SURAT_ADAJAN"))
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andReturn().getResponse().getContentAsString(), "$.trackingNumber");

        // the customer tracks (and may demo-advance) the return as well
        advance(t, TrackingTokens.customer(customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED_UP"));
        assertThat(getJson(t, TrackingTokens.customer(customer))).isEqualTo(getJson(t, TrackingTokens.repairer(repairer)));
        send(get("/api/tracking/repair-requests/{id}/deliveries", r.getId()), TrackingTokens.customer(customer), null)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].deliveryType").value("CUSTOMER_TO_REPAIRER"))
                .andExpect(jsonPath("$[1].deliveryType").value("REPAIRER_TO_CUSTOMER"));
    }

    @Test
    void returnLegRules() throws Exception {
        String rep = TrackingTokens.repairer(repairer);

        RepairRequest inProgress = repair(DeliveryMode.SHIPPING, RepairStatus.IN_PROGRESS);
        book(rep, body("REPAIRER_TO_CUSTOMER", inProgress.getId(), null, null, "AHD_VASTRAPUR"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("IN_PROGRESS"));

        // cancelled before the item ever reached the workshop: nothing to return
        RepairRequest cancelledEarly = repair(DeliveryMode.SHIPPING, RepairStatus.CANCELLED);
        book(rep, body("REPAIRER_TO_CUSTOMER", cancelledEarly.getId(), null, null, "AHD_VASTRAPUR"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("CANCELLED"));

        // quote rejected after the item was delivered to the workshop: it goes back
        RepairRequest rejected = repair(DeliveryMode.SHIPPING, RepairStatus.ACCEPTED);
        String inbound = bookInbound(rejected);
        setStatus(rejected, RepairStatus.QUOTE_REJECTED);
        book(rep, body("REPAIRER_TO_CUSTOMER", rejected.getId(), null, null, null))
                .andExpect(status().isConflict()); // inbound not delivered yet
        advanceTo(inbound, TrackingTokens.customer(customer), 7);
        book(rep, body("REPAIRER_TO_CUSTOMER", rejected.getId(), null, null, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.destination.code").value("SURAT_ADAJAN"));
        book(rep, body("REPAIRER_TO_CUSTOMER", rejected.getId(), null, null, null))
                .andExpect(status().isConflict());

        // completed, no inbound leg (e.g. dropped off in person): the destination must be chosen
        RepairRequest completed = repair(DeliveryMode.PICKUP_DROP, RepairStatus.COMPLETED);
        book(rep, body("REPAIRER_TO_CUSTOMER", completed.getId(), null, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.destinationCode").exists());
        book(rep, body("REPAIRER_TO_CUSTOMER", completed.getId(), null, null, "GNR_SECTOR_21"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin.code").value("AHD_CG_ROAD"));
    }

    /* ───────────────────────────── store → customer ───────────────────────────── */

    @Test
    void sellerShipsAPaidOrderAndBuyerSeesTheSameTracking() throws Exception {
        long seller = customer;
        long buyer = ACCOUNTS.incrementAndGet();
        long orderId = ORDER_IDS.incrementAndGet();
        order(orderId, "CONFIRMED", buyer, seller);

        String json = book(TrackingTokens.customer(seller), body("STORE_TO_CUSTOMER", null, orderId, "SURAT_VARACHHA", "AHD_MANINAGAR"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.simulated").value(true))
                .andExpect(jsonPath("$.deliveryType").value("STORE_TO_CUSTOMER"))
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.repairRequestId").value(nullValue()))
                .andExpect(jsonPath("$.referenceNumber").value("ORD-20261125-TEST" + orderId))
                .andExpect(jsonPath("$.customerAccountId").value(buyer))
                .andExpect(jsonPath("$.partnerAccountId").value(seller))
                .andExpect(jsonPath("$.origin.city").value("Surat"))
                .andExpect(jsonPath("$.destination.city").value("Ahmedabad"))
                .andReturn().getResponse().getContentAsString();
        String t = JsonPath.read(json, "$.trackingNumber");

        advanceTo(t, TrackingTokens.customer(buyer), 3);
        assertThat(getJson(t, TrackingTokens.customer(buyer))).isEqualTo(getJson(t, TrackingTokens.customer(seller)));

        send(get("/api/tracking/orders/{id}/deliveries", orderId), TrackingTokens.customer(buyer), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].trackingNumber").value(t))
                .andExpect(jsonPath("$[0].progressPercent").value(40));
        send(get("/api/tracking/orders/{id}/deliveries", orderId), TrackingTokens.customer(stranger), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        send(get("/api/tracking/deliveries/{t}", t), TrackingTokens.customer(stranger), null)
                .andExpect(status().isNotFound());

        book(TrackingTokens.customer(seller), body("STORE_TO_CUSTOMER", null, orderId, "SURAT_VARACHHA", "AHD_MANINAGAR"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString(t)));
    }

    @Test
    void storeLegRules() throws Exception {
        long seller = customer;
        long buyer = ACCOUNTS.incrementAndGet();

        long paid = ORDER_IDS.incrementAndGet();
        order(paid, "CONFIRMED", buyer, seller);
        book(TrackingTokens.customer(buyer), body("STORE_TO_CUSTOMER", null, paid, "SURAT_VARACHHA", "AHD_MANINAGAR"))
                .andExpect(status().isForbidden());
        book(TrackingTokens.customer(stranger), body("STORE_TO_CUSTOMER", null, paid, "SURAT_VARACHHA", "AHD_MANINAGAR"))
                .andExpect(status().isNotFound());
        book(TrackingTokens.customer(seller), body("STORE_TO_CUSTOMER", null, paid, "SURAT_VARACHHA", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.destinationCode").exists());
        book(TrackingTokens.customer(seller), body("STORE_TO_CUSTOMER", 9L, paid, "SURAT_VARACHHA", "AHD_MANINAGAR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.repairRequestId").exists());

        long unpaid = ORDER_IDS.incrementAndGet();
        order(unpaid, "PENDING_PAYMENT", buyer, seller);
        book(TrackingTokens.customer(seller), body("STORE_TO_CUSTOMER", null, unpaid, "SURAT_VARACHHA", "AHD_MANINAGAR"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("PENDING_PAYMENT"));

        long missing = ORDER_IDS.incrementAndGet();
        when(orderService.getOrder(missing)).thenThrow(new OrderServiceException(404, "Order " + missing + " not found", null));
        book(TrackingTokens.customer(seller), body("STORE_TO_CUSTOMER", null, missing, "SURAT_VARACHHA", "AHD_MANINAGAR"))
                .andExpect(status().isNotFound());

        long offline = ORDER_IDS.incrementAndGet();
        when(orderService.getOrder(offline)).thenThrow(new OrderServiceException(503, "Order & Payment service is unavailable", null));
        book(TrackingTokens.customer(seller), body("STORE_TO_CUSTOMER", null, offline, "SURAT_VARACHHA", "AHD_MANINAGAR"))
                .andExpect(status().isServiceUnavailable());

        assertThat(deliveries.findByOrderExternalIdOrderByIdAsc(paid)).isEmpty();
        assertThat(deliveries.findByOrderExternalIdOrderByIdAsc(unpaid)).isEmpty();
    }
}
