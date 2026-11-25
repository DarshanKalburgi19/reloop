package com.reloop.repair.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.reloop.repair.entity.RepairRequest;
import com.reloop.repair.entity.RepairerProfile;
import com.reloop.repair.enums.DeliveryMode;
import com.reloop.repair.enums.InventoryMovementType;
import com.reloop.repair.enums.RepairServiceCategory;
import com.reloop.repair.enums.RepairStatus;
import com.reloop.repair.repository.InventoryMovementRepository;
import com.reloop.repair.repository.RepairRequestRepository;
import com.reloop.repair.repository.RepairerProfileRepository;
import com.reloop.repair.repository.SparePartRepository;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Spare-parts inventory over HTTP: adding and editing parts, stock in / used in repair / adjustment movements,
 * the never-negative rule, owner-only access, low-stock alerts and inventory valuation.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:inventory-api-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class InventoryApiTest {

    private static final AtomicLong ACCOUNTS = new AtomicLong(7600);
    private static final AtomicLong REQUEST_NUMBERS = new AtomicLong(1);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RepairerProfileRepository profiles;
    @Autowired
    private RepairRequestRepository requests;
    @Autowired
    private SparePartRepository parts;
    @Autowired
    private InventoryMovementRepository movements;

    private long account;
    private RepairerProfile profile;

    @BeforeEach
    void setUp() {
        account = ACCOUNTS.incrementAndGet();
        profile = profileFor(account);
    }

    /* ───────────────────────────── helpers ───────────────────────────── */

    private RepairerProfile profileFor(long accountId) {
        RepairerProfile p = new RepairerProfile();
        p.setAccountExternalId(accountId);
        p.setBusinessName("Parts Lab " + accountId);
        p.setCity("Ahmedabad");
        p.setAcceptsInPerson(true);
        return profiles.saveAndFlush(p);
    }

    private RepairRequest repairFor(RepairerProfile owner, RepairStatus status) {
        RepairRequest r = new RepairRequest();
        r.setRequestNumber("REQ-INV-" + REQUEST_NUMBERS.getAndIncrement());
        r.setCustomerAccountId(99L);
        r.setRepairerProfile(owner);
        r.setDeliveryMode(DeliveryMode.IN_PERSON);
        r.setItemCategory(RepairServiceCategory.MOBILE);
        r.setItemDescription("Redmi Note 12");
        r.setStatus(status);
        return requests.saveAndFlush(r);
    }

    private String me() {
        return InventoryTokens.repairer(account);
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

    private static String partJson(String name, String category, String unitCost, Integer quantity, Integer threshold) {
        return "{\"partName\":\"" + name + "\",\"category\":\"" + category + "\",\"unitCost\":" + unitCost
                + (quantity == null ? "" : ",\"quantity\":" + quantity)
                + (threshold == null ? "" : ",\"lowStockThreshold\":" + threshold) + "}";
    }

    private long addPart(String name, String category, String unitCost, int quantity, int threshold) throws Exception {
        String json = send(post("/api/inventory/parts"), me(), partJson(name, category, unitCost, quantity, threshold))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private ResultActions use(long partId, long repairId, int quantity) throws Exception {
        return send(post("/api/inventory/parts/{id}/use", partId), me(),
                "{\"repairRequestId\":" + repairId + ",\"quantity\":" + quantity + ",\"note\":\"Fitted\"}");
    }

    private int quantityOf(long partId) {
        return parts.findById(partId).orElseThrow().getQuantity();
    }

    /* ───────────────────────────── adding and editing parts ───────────────────────────── */

    @Test
    void addPartWithOpeningStockRecordsAStockInMovement() throws Exception {
        String json = send(post("/api/inventory/parts"), me(),
                "{\"partName\":\"Li-ion battery 5000mAh\",\"category\":\"BATTERY\",\"unitCost\":450.00,"
                        + "\"quantity\":10,\"lowStockThreshold\":3,\"description\":\"Fits Redmi Note 12\"}")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(".*/api/inventory/parts/\\d+")))
                .andExpect(jsonPath("$.repairerId").value(profile.getId().intValue()))
                .andExpect(jsonPath("$.partName").value("Li-ion battery 5000mAh"))
                .andExpect(jsonPath("$.category").value("BATTERY"))
                .andExpect(jsonPath("$.sku", matchesPattern("BAT-[2-9A-HJKMNP-Z]{8}")))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.unitCostPaise").value(45000))
                .andExpect(jsonPath("$.unitCostDisplay").value("₹450"))
                .andExpect(jsonPath("$.lowStockThreshold").value(3))
                .andExpect(jsonPath("$.stockStatus").value("IN_STOCK"))
                .andExpect(jsonPath("$.lowStock").value(false))
                .andExpect(jsonPath("$.stockValuePaise").value(450000))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(json, "$.id")).longValue();

        send(get("/api/inventory/parts/{id}/movements", id), me(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].movementType").value("STOCK_IN"))
                .andExpect(jsonPath("$.content[0].quantity").value(10))
                .andExpect(jsonPath("$.content[0].quantityChange").value(10))
                .andExpect(jsonPath("$.content[0].stockAfter").value(10))
                .andExpect(jsonPath("$.content[0].sparePartId").value((int) id))
                .andExpect(jsonPath("$.content[0].repairerId").value(profile.getId().intValue()))
                .andExpect(jsonPath("$.content[0].repairRequestId").value(nullValue()))
                .andExpect(jsonPath("$.content[0].note").value("Opening stock"))
                .andExpect(jsonPath("$.content[0].movementDate").exists());
    }

    @Test
    void addPartWithoutStockRecordsNoMovementAndDefaultsThreshold() throws Exception {
        long id = ((Number) JsonPath.read(send(post("/api/inventory/parts"), me(),
                partJson("Tempered glass", "SCREEN_DISPLAY", "35.5", null, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(0))
                .andExpect(jsonPath("$.lowStockThreshold").value(5))
                .andExpect(jsonPath("$.unitCostPaise").value(3550))
                .andExpect(jsonPath("$.stockStatus").value("OUT_OF_STOCK"))
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();

        assertThat(movements.countBySparePart_Id(id)).isZero();
    }

    @Test
    void skuIsNormalisedAndUniquePerRepairer() throws Exception {
        send(post("/api/inventory/parts"), me(),
                "{\"partName\":\"iPhone 11 screen\",\"category\":\"SCREEN_DISPLAY\",\"unitCost\":2100,\"sku\":\" scr-ip11-blk \"}")
                .andExpect(status().isBadRequest()); // leading space fails the SKU pattern
        send(post("/api/inventory/parts"), me(),
                "{\"partName\":\"iPhone 11 screen\",\"category\":\"SCREEN_DISPLAY\",\"unitCost\":2100,\"sku\":\"scr-ip11-blk\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("SCR-IP11-BLK"));
        send(post("/api/inventory/parts"), me(),
                "{\"partName\":\"Another screen\",\"category\":\"SCREEN_DISPLAY\",\"unitCost\":2100,\"sku\":\"SCR-IP11-BLK\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("SCR-IP11-BLK")));

        // another repairer may use the same SKU
        long other = ACCOUNTS.incrementAndGet();
        profileFor(other);
        send(post("/api/inventory/parts"), InventoryTokens.repairer(other),
                "{\"partName\":\"iPhone 11 screen\",\"category\":\"SCREEN_DISPLAY\",\"unitCost\":2000,\"sku\":\"SCR-IP11-BLK\"}")
                .andExpect(status().isCreated());
    }

    @Test
    void invalidPartsAreRejectedWithFieldErrors() throws Exception {
        send(post("/api/inventory/parts"), me(), "{\"unitCost\":-1,\"quantity\":-2,\"lowStockThreshold\":-1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasKey("partName")))
                .andExpect(jsonPath("$.fieldErrors", hasKey("category")))
                .andExpect(jsonPath("$.fieldErrors.unitCost").value("unitCost can't be negative"))
                .andExpect(jsonPath("$.fieldErrors.quantity").value("quantity can't be negative"))
                .andExpect(jsonPath("$.fieldErrors.lowStockThreshold").value("lowStockThreshold can't be negative"));
        send(post("/api/inventory/parts"), me(), partJson("Screw kit", "MECHANICAL", "10.555", 1, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.unitCost").value("unitCost must have at most 2 decimals"));
        send(post("/api/inventory/parts"), me(), partJson("Screw kit", "NOT_A_CATEGORY", "10", 1, 1))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editingInfoNeverChangesTheQuantity() throws Exception {
        long id = addPart("USB-C port", "CHARGING_POWER", "80", 7, 2);

        send(patch("/api/inventory/parts/{id}", id), me(),
                "{\"partName\":\"USB-C charging port\",\"category\":\"CABLES_CONNECTORS\",\"unitCost\":95.50,"
                        + "\"lowStockThreshold\":8,\"sku\":\"usbc-port-01\",\"description\":\"Type-C 16 pin\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partName").value("USB-C charging port"))
                .andExpect(jsonPath("$.category").value("CABLES_CONNECTORS"))
                .andExpect(jsonPath("$.unitCostPaise").value(9550))
                .andExpect(jsonPath("$.lowStockThreshold").value(8))
                .andExpect(jsonPath("$.sku").value("USBC-PORT-01"))
                .andExpect(jsonPath("$.description").value("Type-C 16 pin"))
                .andExpect(jsonPath("$.quantity").value(7))
                .andExpect(jsonPath("$.lowStock").value(true))
                .andExpect(jsonPath("$.stockStatus").value("LOW_STOCK"));
        assertThat(movements.countBySparePart_Id(id)).isEqualTo(1); // only the opening stock

        send(patch("/api/inventory/parts/{id}", id), me(), "{\"description\":\"\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(nullValue()));
        send(patch("/api/inventory/parts/{id}", id), me(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.body").exists());
        send(patch("/api/inventory/parts/{id}", id), me(), "{\"partName\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.partName").exists());
        // unknown "quantity" field is ignored: still 7
        send(patch("/api/inventory/parts/{id}", id), me(), "{\"lowStockThreshold\":1,\"quantity\":500}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(7));
    }

    @Test
    void changingSkuToOneAlreadyUsedIsAConflict() throws Exception {
        send(post("/api/inventory/parts"), me(),
                "{\"partName\":\"A\",\"category\":\"OTHER\",\"unitCost\":1,\"sku\":\"DUP-1\"}").andExpect(status().isCreated());
        long b = addPart("B", "OTHER", "1", 0, 0);

        send(patch("/api/inventory/parts/{id}", b), me(), "{\"sku\":\"dup-1\"}")
                .andExpect(status().isConflict());
    }

    /* ───────────────────────────── stock movements ───────────────────────────── */

    @Test
    void stockInIncreasesQuantityAndAveragesTheUnitCost() throws Exception {
        long id = addPart("Display assembly", "SCREEN_DISPLAY", "100", 10, 2);

        send(post("/api/inventory/parts/{id}/stock-in", id), me(),
                "{\"quantity\":10,\"unitCost\":120,\"note\":\"Invoice #A-17\",\"movementDate\":\"2026-11-20T11:30:00\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.part.quantity").value(20))
                .andExpect(jsonPath("$.part.unitCostPaise").value(11000))
                .andExpect(jsonPath("$.part.stockValuePaise").value(220000))
                .andExpect(jsonPath("$.movement.movementType").value("STOCK_IN"))
                .andExpect(jsonPath("$.movement.quantity").value(10))
                .andExpect(jsonPath("$.movement.stockAfter").value(20))
                .andExpect(jsonPath("$.movement.unitCostPaise").value(12000))
                .andExpect(jsonPath("$.movement.totalCostPaise").value(120000))
                .andExpect(jsonPath("$.movement.note").value("Invoice #A-17"))
                .andExpect(jsonPath("$.movement.movementDate").value("2026-11-20T11:30:00"))
                .andExpect(jsonPath("$.lowStockAlert").value(false))
                .andExpect(jsonPath("$.alertMessage").value(nullValue()));

        // without a price the current cost is used and the average is unchanged
        send(post("/api/inventory/parts/{id}/stock-in", id), me(), "{\"quantity\":5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.part.quantity").value(25))
                .andExpect(jsonPath("$.part.unitCostPaise").value(11000))
                .andExpect(jsonPath("$.movement.unitCostPaise").value(11000));
    }

    @Test
    void invalidStockInIsRejected() throws Exception {
        long id = addPart("Speaker", "AUDIO", "60", 1, 1);

        send(post("/api/inventory/parts/{id}/stock-in", id), me(), "{\"quantity\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.quantity").value("quantity must be at least 1"));
        send(post("/api/inventory/parts/{id}/stock-in", id), me(), "{\"quantity\":-3}")
                .andExpect(status().isBadRequest());
        send(post("/api/inventory/parts/{id}/stock-in", id), me(), "{\"quantity\":2,\"movementDate\":\"2999-01-01T10:00:00\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.movementDate").value("movementDate can't be in the future"));
        send(post("/api/inventory/parts/{id}/stock-in", id), me(), "{\"quantity\":1000000}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.quantity", containsString("1000000")));
        assertThat(quantityOf(id)).isEqualTo(1);
    }

    @Test
    void usingPartsInARepairDecreasesStockAndIsLinkedToTheRepair() throws Exception {
        long id = addPart("Charging IC", "CIRCUIT_BOARD", "250", 8, 2);
        RepairRequest repair = repairFor(profile, RepairStatus.IN_PROGRESS);

        use(id, repair.getId(), 3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.part.quantity").value(5))
                .andExpect(jsonPath("$.movement.movementType").value("USED_IN_REPAIR"))
                .andExpect(jsonPath("$.movement.quantity").value(3))
                .andExpect(jsonPath("$.movement.quantityChange").value(-3))
                .andExpect(jsonPath("$.movement.stockAfter").value(5))
                .andExpect(jsonPath("$.movement.repairRequestId").value(repair.getId().intValue()))
                .andExpect(jsonPath("$.movement.repairRequestNumber").value(repair.getRequestNumber()))
                .andExpect(jsonPath("$.movement.unitCostPaise").value(25000))
                .andExpect(jsonPath("$.movement.totalCostPaise").value(75000));

        RepairRequest paid = repairFor(profile, RepairStatus.PAID);
        use(id, paid.getId(), 1).andExpect(status().isOk()).andExpect(jsonPath("$.part.quantity").value(4));

        send(get("/api/inventory/repair-requests/{rid}/parts", repair.getId()), me(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestNumber").value(repair.getRequestNumber()))
                .andExpect(jsonPath("$.netUnitsUsed").value(3))
                .andExpect(jsonPath("$.partsCostPaise").value(75000))
                .andExpect(jsonPath("$.movements", hasSize(1)));
    }

    @Test
    void quantityNeverBecomesNegative() throws Exception {
        long id = addPart("Camera module", "CAMERA", "900", 2, 1);
        RepairRequest repair = repairFor(profile, RepairStatus.IN_PROGRESS);

        use(id, repair.getId(), 3)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.availableQuantity").value(2))
                .andExpect(jsonPath("$.requestedQuantity").value(3))
                .andExpect(jsonPath("$.sparePartId").value((int) id))
                .andExpect(jsonPath("$.message", containsString("Not enough stock")));
        send(post("/api/inventory/parts/{id}/adjust", id), me(), "{\"quantityChange\":-5,\"reason\":\"Damaged\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.availableQuantity").value(2));
        send(post("/api/inventory/parts/{id}/adjust", id), me(), "{\"newQuantity\":-1,\"reason\":\"Count\"}")
                .andExpect(status().isBadRequest());

        assertThat(quantityOf(id)).isEqualTo(2);
        assertThat(movements.countBySparePart_Id(id)).isEqualTo(1); // refused changes are not recorded

        use(id, repair.getId(), 2).andExpect(status().isOk())
                .andExpect(jsonPath("$.part.quantity").value(0))
                .andExpect(jsonPath("$.part.stockStatus").value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$.lowStockAlert").value(true))
                .andExpect(jsonPath("$.alertMessage").value("'Camera module' is out of stock"));
        use(id, repair.getId(), 1).andExpect(status().isConflict());
        assertThat(quantityOf(id)).isZero();
    }

    @Test
    void partsCanOnlyBeUsedOnOwnRepairsThatArePaidOrInProgress() throws Exception {
        long id = addPart("Back glass", "MECHANICAL", "150", 5, 1);

        for (RepairStatus notYet : new RepairStatus[] {RepairStatus.REQUESTED, RepairStatus.QUOTED,
                RepairStatus.AWAITING_PAYMENT, RepairStatus.COMPLETED, RepairStatus.CANCELLED}) {
            use(id, repairFor(profile, notYet).getId(), 1)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.currentStatus").value(notYet.name()));
        }
        RepairerProfile stranger = profileFor(ACCOUNTS.incrementAndGet());
        use(id, repairFor(stranger, RepairStatus.IN_PROGRESS).getId(), 1).andExpect(status().isNotFound());
        use(id, 987654321L, 1).andExpect(status().isNotFound());
        send(post("/api/inventory/parts/{id}/use", id), me(), "{\"quantity\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.repairRequestId").exists());

        assertThat(quantityOf(id)).isEqualTo(5);
        assertThat(movements.countBySparePart_Id(id)).isEqualTo(1);
    }

    @Test
    void adjustmentsSetOrCorrectTheQuantityWithAReason() throws Exception {
        long id = addPart("Thermal paste", "CONSUMABLES", "40", 10, 2);

        send(post("/api/inventory/parts/{id}/adjust", id), me(), "{\"newQuantity\":7,\"reason\":\"Monthly stock count\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.part.quantity").value(7))
                .andExpect(jsonPath("$.movement.movementType").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.movement.quantityChange").value(-3))
                .andExpect(jsonPath("$.movement.note").value("Monthly stock count"));
        RepairRequest repair = repairFor(profile, RepairStatus.COMPLETED);
        send(post("/api/inventory/parts/{id}/adjust", id), me(),
                "{\"quantityChange\":2,\"reason\":\"Unused tubes returned\",\"repairRequestId\":" + repair.getId() + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.part.quantity").value(9))
                .andExpect(jsonPath("$.movement.quantityChange").value(2))
                .andExpect(jsonPath("$.movement.repairRequestId").value(repair.getId().intValue()));

        send(post("/api/inventory/parts/{id}/adjust", id), me(), "{\"newQuantity\":9,\"reason\":\"Recount\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newQuantity", containsString("already 9")));
        send(post("/api/inventory/parts/{id}/adjust", id), me(), "{\"newQuantity\":3,\"quantityChange\":1,\"reason\":\"x\"}")
                .andExpect(status().isBadRequest());
        send(post("/api/inventory/parts/{id}/adjust", id), me(), "{\"reason\":\"nothing\"}")
                .andExpect(status().isBadRequest());
        send(post("/api/inventory/parts/{id}/adjust", id), me(), "{\"newQuantity\":3}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").exists());

        assertThat(quantityOf(id)).isEqualTo(9);
        assertThat(movements.countBySparePart_Id(id)).isEqualTo(3);
    }

    @Test
    void everyMovementIsRecordedWithARunningBalance() throws Exception {
        long id = addPart("Flex cable", "CABLES_CONNECTORS", "30", 4, 1);
        RepairRequest repair = repairFor(profile, RepairStatus.IN_PROGRESS);
        send(post("/api/inventory/parts/{id}/stock-in", id), me(), "{\"quantity\":6}").andExpect(status().isOk());
        use(id, repair.getId(), 2).andExpect(status().isOk());
        send(post("/api/inventory/parts/{id}/adjust", id), me(), "{\"quantityChange\":-1,\"reason\":\"Torn\"}")
                .andExpect(status().isOk());

        var ledger = movements.findBySparePart_IdOrderByCreatedAt(id).stream()
                .sorted((a, b) -> a.getId().compareTo(b.getId())).toList();
        assertThat(ledger).extracting(m -> m.getMovementType().name())
                .containsExactly("STOCK_IN", "STOCK_IN", "USED_IN_REPAIR", "ADJUSTMENT");
        assertThat(ledger).extracting(m -> m.getStockAfter()).containsExactly(4, 10, 8, 7);
        assertThat(ledger.stream().mapToInt(m -> m.getQuantityChange()).sum()).isEqualTo(quantityOf(id));

        send(get("/api/inventory/movements").param("type", "USED_IN_REPAIR"), me(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].repairRequestId").value(repair.getId().intValue()));
        send(get("/api/inventory/movements").param("sparePartId", Long.toString(id)), me(), null)
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].movementType").value("ADJUSTMENT")); // newest first
        String today = LocalDate.now().toString();
        send(get("/api/inventory/movements").param("from", today).param("to", today), me(), null)
                .andExpect(jsonPath("$.totalElements").value(4));
        send(get("/api/inventory/movements").param("to", "2020-01-01"), me(), null)
                .andExpect(jsonPath("$.totalElements").value(0));
        send(get("/api/inventory/movements").param("from", "2026-02-01").param("to", "2026-01-01"), me(), null)
                .andExpect(status().isBadRequest());
    }

    /* ───────────────────────────── low stock and valuation ───────────────────────────── */

    @Test
    void lowStockAlertListsPartsAtOrBelowTheirThreshold() throws Exception {
        send(get("/api/inventory/low-stock"), me(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowStockAlert").value(false))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.alertMessage").value(nullValue()));

        long battery = addPart("Battery", "BATTERY", "400", 6, 5);
        addPart("Screen", "SCREEN_DISPLAY", "2000", 0, 1);
        addPart("Glue", "CONSUMABLES", "50", 50, 5);
        long discontinued = addPart("Old flex", "CABLES_CONNECTORS", "10", 1, 5);
        send(post("/api/inventory/parts/{id}/discontinue", discontinued), me(), null).andExpect(status().isOk());

        RepairRequest repair = repairFor(profile, RepairStatus.IN_PROGRESS);
        use(battery, repair.getId(), 2)
                .andExpect(jsonPath("$.lowStockAlert").value(true))
                .andExpect(jsonPath("$.alertMessage").value("'Battery' is low on stock: 4 left (threshold 5)"));

        send(get("/api/inventory/low-stock"), me(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowStockAlert").value(true))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.outOfStockCount").value(1))
                .andExpect(jsonPath("$.alertMessage").value("2 parts are at or below the low-stock threshold (1 out of stock)"))
                .andExpect(jsonPath("$.items[*].partName", contains("Screen", "Battery")))
                .andExpect(jsonPath("$.items[*].lowStock", everyItem(is(true))));

        send(get("/api/inventory/parts").param("lowStockOnly", "true"), me(), null)
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void summaryShowsTotalsAndInventoryValueSeparatelyFromEarnings() throws Exception {
        long battery = addPart("Battery", "BATTERY", "400.00", 10, 3);     // ₹4,000
        addPart("Screen", "SCREEN_DISPLAY", "1999.50", 2, 2);               // ₹3,999 (low)
        long flex = addPart("Old flex", "CABLES_CONNECTORS", "25", 4, 1);   // ₹100 (discontinued, still on hand)
        addPart("Charging port", "CHARGING_POWER", "80", 0, 1);             // ₹0 (out of stock)
        send(post("/api/inventory/parts/{id}/discontinue", flex), me(), null).andExpect(status().isOk());
        use(battery, repairFor(profile, RepairStatus.IN_PROGRESS).getId(), 1).andExpect(status().isOk()); // -₹400

        send(get("/api/inventory/summary"), me(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repairerId").value(profile.getId().intValue()))
                .andExpect(jsonPath("$.totalParts").value(3))
                .andExpect(jsonPath("$.discontinuedParts").value(1))
                .andExpect(jsonPath("$.totalUnits").value(9 + 2 + 4))
                .andExpect(jsonPath("$.lowStockCount").value(2))
                .andExpect(jsonPath("$.outOfStockCount").value(1))
                .andExpect(jsonPath("$.lowStockAlert").value(true))
                .andExpect(jsonPath("$.inventoryValuePaise").value(360000 + 399900 + 10000))
                .andExpect(jsonPath("$.inventoryValueDisplay").value("₹7,699"))
                .andExpect(jsonPath("$.stockPurchasedCostPaise").value(400000 + 399900 + 10000))
                .andExpect(jsonPath("$.partsUsedInRepairsCostPaise").value(40000))
                .andExpect(jsonPath("$.includedInRepairEarnings").value(false))
                .andExpect(jsonPath("$.valuationNote", containsString("not included in your repair earnings")))
                .andExpect(jsonPath("$.byCategory[0].category").value("SCREEN_DISPLAY"))
                .andExpect(jsonPath("$.byCategory[0].valuePaise").value(399900))
                .andExpect(jsonPath("$.byCategory", hasSize(4)))
                .andExpect(jsonPath("$", not(hasKey("earnings"))));
    }

    @Test
    void emptyInventorySummaryIsAllZeros() throws Exception {
        send(get("/api/inventory/summary"), me(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalParts").value(0))
                .andExpect(jsonPath("$.inventoryValuePaise").value(0))
                .andExpect(jsonPath("$.inventoryValueDisplay").value("₹0"))
                .andExpect(jsonPath("$.lowStockAlert").value(false))
                .andExpect(jsonPath("$.byCategory", hasSize(0)));
    }

    /* ───────────────────────────── listing and discontinuing ───────────────────────────── */

    @Test
    void listFiltersByCategoryTextAndStatus() throws Exception {
        addPart("Samsung A52 battery", "BATTERY", "500", 3, 1);
        addPart("iPhone 12 battery", "BATTERY", "900", 3, 1);
        addPart("iPhone 12 screen", "SCREEN_DISPLAY", "3000", 3, 1);
        long gone = addPart("Nokia 3310 battery", "BATTERY", "100", 0, 0);
        send(post("/api/inventory/parts/{id}/discontinue", gone), me(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCONTINUED"));

        send(get("/api/inventory/parts"), me(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].partName",
                        contains("Samsung A52 battery", "iPhone 12 battery", "iPhone 12 screen")));
        send(get("/api/inventory/parts").param("category", "BATTERY"), me(), null)
                .andExpect(jsonPath("$.totalElements").value(2));
        send(get("/api/inventory/parts").param("q", "IPHONE 12"), me(), null)
                .andExpect(jsonPath("$.totalElements").value(2));
        send(get("/api/inventory/parts").param("q", "100%_"), me(), null)
                .andExpect(jsonPath("$.totalElements").value(0));
        send(get("/api/inventory/parts").param("includeDiscontinued", "true").param("category", "BATTERY"), me(), null)
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].status", hasItem("DISCONTINUED")));
        send(get("/api/inventory/parts").param("sort", "quantity").param("includeDiscontinued", "true"), me(), null)
                .andExpect(jsonPath("$.content[0].partName").value("Nokia 3310 battery"));
        send(get("/api/inventory/parts").param("sort", "price"), me(), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sort").exists());
        send(get("/api/inventory/parts").param("size", "500"), me(), null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void discontinuedPartsCantReceiveStockUntilReactivated() throws Exception {
        long id = addPart("Old charger", "CHARGING_POWER", "120", 2, 1);
        send(post("/api/inventory/parts/{id}/discontinue", id), me(), null).andExpect(status().isOk());

        send(post("/api/inventory/parts/{id}/stock-in", id), me(), "{\"quantity\":3}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("discontinued")));
        // remaining units can still be used up
        use(id, repairFor(profile, RepairStatus.IN_PROGRESS).getId(), 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.lowStockAlert").value(false)); // discontinued parts don't alert

        send(post("/api/inventory/parts/{id}/reactivate", id), me(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        send(post("/api/inventory/parts/{id}/stock-in", id), me(), "{\"quantity\":3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.part.quantity").value(4));
    }

    /* ───────────────────────────── access control ───────────────────────────── */

    @Test
    void onlyTheOwnerCanSeeOrChangeTheirStock() throws Exception {
        long id = addPart("Owner's battery", "BATTERY", "300", 5, 1);
        long otherAccount = ACCOUNTS.incrementAndGet();
        RepairerProfile otherProfile = profileFor(otherAccount);
        String other = InventoryTokens.repairer(otherAccount);
        RepairRequest othersRepair = repairFor(otherProfile, RepairStatus.IN_PROGRESS);

        send(get("/api/inventory/parts/{id}", id), other, null).andExpect(status().isNotFound());
        send(patch("/api/inventory/parts/{id}", id), other, "{\"partName\":\"Mine now\"}").andExpect(status().isNotFound());
        send(post("/api/inventory/parts/{id}/stock-in", id), other, "{\"quantity\":5}").andExpect(status().isNotFound());
        send(post("/api/inventory/parts/{id}/use", id), other,
                "{\"repairRequestId\":" + othersRepair.getId() + ",\"quantity\":1}").andExpect(status().isNotFound());
        send(post("/api/inventory/parts/{id}/adjust", id), other, "{\"newQuantity\":0,\"reason\":\"x\"}")
                .andExpect(status().isNotFound());
        send(post("/api/inventory/parts/{id}/discontinue", id), other, null).andExpect(status().isNotFound());
        send(get("/api/inventory/parts/{id}/movements", id), other, null).andExpect(status().isNotFound());
        send(get("/api/inventory/parts"), other, null).andExpect(jsonPath("$.totalElements").value(0));
        send(get("/api/inventory/movements").param("sparePartId", Long.toString(id)), other, null)
                .andExpect(jsonPath("$.totalElements").value(0));
        send(get("/api/inventory/summary"), other, null).andExpect(jsonPath("$.inventoryValuePaise").value(0));
        // the owner can't record usage on someone else's repair either
        send(get("/api/inventory/repair-requests/{rid}/parts", othersRepair.getId()), me(), null)
                .andExpect(status().isNotFound());

        send(get("/api/inventory/parts/{id}", id), me(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partName").value("Owner's battery"))
                .andExpect(jsonPath("$.quantity").value(5));
        assertThat(movements.countBySparePart_Id(id)).isEqualTo(1);
    }

    @Test
    void customersAnonymousCallersAndRepairersWithoutAProfileAreRefused() throws Exception {
        send(get("/api/inventory/parts"), null, null).andExpect(status().isUnauthorized());
        send(post("/api/inventory/parts"), null, partJson("X", "OTHER", "1", 1, 1)).andExpect(status().isUnauthorized());
        send(get("/api/inventory/summary"), InventoryTokens.customer(account), null).andExpect(status().isForbidden());
        send(post("/api/inventory/parts"), InventoryTokens.customer(account), partJson("X", "OTHER", "1", 1, 1))
                .andExpect(status().isForbidden());

        long noProfile = ACCOUNTS.incrementAndGet();
        send(get("/api/inventory/summary"), InventoryTokens.repairer(noProfile), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("workshop profile")));
        send(post("/api/inventory/parts"), InventoryTokens.repairer(noProfile), partJson("X", "OTHER", "1", 1, 1))
                .andExpect(status().isNotFound());
        assertThat(parts.findByRepairerProfile_Id(profile.getId())).isEmpty();
    }

    @Test
    void unknownPartIsNotFound() throws Exception {
        send(get("/api/inventory/parts/{id}", 987654321L), me(), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Spare part not found: 987654321"));
        send(post("/api/inventory/parts/{id}/stock-in", 987654321L), me(), "{\"quantity\":1}")
                .andExpect(status().isNotFound());
    }

    @Test
    void movementTypesAreExactlyTheThreeRequired() {
        assertThat(InventoryMovementType.values()).extracting(Enum::name)
                .containsExactly("STOCK_IN", "USED_IN_REPAIR", "ADJUSTMENT");
    }
}
