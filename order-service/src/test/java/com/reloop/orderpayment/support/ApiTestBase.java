package com.reloop.orderpayment.support;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reloop.orderpayment.client.ProductSale;
import com.reloop.orderpayment.client.ProductServiceClient;
import com.reloop.orderpayment.client.RepairServiceClient;
import com.reloop.orderpayment.repository.CommissionRecordRepository;
import com.reloop.orderpayment.repository.LedgerEntryRepository;
import com.reloop.orderpayment.repository.PaymentTransactionRepository;
import com.reloop.orderpayment.repository.ProviderPayoutRepository;
import com.reloop.orderpayment.repository.PurchaseOrderRepository;
import com.reloop.orderpayment.repository.RefundRecordRepository;
import com.reloop.orderpayment.repository.RepairQuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Shared set-up for order-service API tests: one Spring context (same mocks everywhere), product-service and
 * repair-service replaced by Mockito mocks, and an empty database before every test.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class ApiTestBase {

    public static final String KEY = "test-internal-key";
    public static final long BUYER = 501L;
    public static final long SELLER = 701L;
    public static final long STRANGER = 999L;
    public static final long REPAIRER_ACCOUNT = 601L;
    public static final long REPAIRER_PROFILE = 77L;
    public static final long PRODUCT = 42L;

    private static final String[] TABLES = {"ledger_entries", "refund_records", "provider_payouts", "commission_records",
        "payment_transactions", "order_items", "purchase_orders", "repair_quotes", "fee_rate_configs"};

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected PurchaseOrderRepository orders;

    @Autowired
    protected RepairQuoteRepository quotes;

    @Autowired
    protected PaymentTransactionRepository payments;

    @Autowired
    protected CommissionRecordRepository commissions;

    @Autowired
    protected ProviderPayoutRepository payouts;

    @Autowired
    protected RefundRecordRepository refunds;

    @Autowired
    protected LedgerEntryRepository ledger;

    @MockitoBean
    protected RepairServiceClient repairService;

    @MockitoBean
    protected ProductServiceClient productService;

    @BeforeEach
    void wipeDatabaseAndStubProducts() {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : TABLES) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
        stubProduct(PRODUCT, SELLER, 100_000L, "Used Sony headphones");
    }

    /** product-service answers like the real internal API for this listing. */
    protected void stubProduct(long productId, long sellerId, long pricePaise, String title) {
        when(productService.reserve(eq(productId), anyLong(), anyString())).thenAnswer(inv -> new ProductSale(productId,
                title, sellerId, inv.getArgument(1), pricePaise, "RESERVED", "RLP-TEST-0001", inv.getArgument(2)));
        when(productService.confirmSale(eq(productId), anyLong(), anyString(), anyString())).thenAnswer(inv ->
                new ProductSale(productId, title, sellerId, inv.getArgument(1), pricePaise, "SOLD", "RLP-TEST-0001",
                        inv.getArgument(2)));
        when(productService.release(eq(productId), anyString(), org.mockito.ArgumentMatchers.any())).thenAnswer(inv ->
                new ProductSale(productId, title, sellerId, null, pricePaise, "ACTIVE", "RLP-TEST-0001", null));
    }

    /* ───────────────────────────── HTTP helpers ───────────────────────────── */

    protected ResultActions postJson(String url, String token, String body) throws Exception {
        var req = post(url).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            req.header("Authorization", token);
        }
        return mvc.perform(req);
    }

    protected JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** Places an order for {@code productId} as {@code buyer}; returns the order JSON. */
    protected JsonNode placeOrder(long buyer, long productId) throws Exception {
        return body(postJson("/api/orders", Tokens.customer(buyer), "{\"productId\":" + productId + "}")
                .andExpect(status().isCreated()));
    }

    protected ResultActions payOrder(long buyer, long orderId, String method, String outcome, long amountPaise)
            throws Exception {
        return postJson("/api/payments/simulate", Tokens.customer(buyer),
                "{\"orderId\":%d,\"method\":\"%s\",\"outcome\":\"%s\",\"amountPaise\":%d}"
                        .formatted(orderId, method, outcome, amountPaise));
    }

    /** Places and successfully pays an order; returns the transaction JSON. */
    protected JsonNode paidOrder(long buyer, long productId) throws Exception {
        JsonNode order = placeOrder(buyer, productId);
        return body(payOrder(buyer, order.get("id").asLong(), "UPI", "SUCCESS", order.get("totalPaise").asLong())
                .andExpect(status().isCreated()));
    }

    /** Creates a SENT repair quote through the internal API; returns its id. */
    protected long repairQuote(long requestId, long basePaise) throws Exception {
        String body = """
                {"repairRequestId":%d,"requestNumber":"RR-20261125-ABCDEFGH","repairerProfileId":%d,
                 "customerAccountId":%d,"baseAmountPaise":%d,"summary":"Replace screen","repairerAccountId":%d}
                """.formatted(requestId, REPAIRER_PROFILE, BUYER, basePaise, REPAIRER_ACCOUNT);
        JsonNode q = body(mvc.perform(post("/api/internal/repair-quotes").header("X-Internal-Api-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()));
        return q.get("id").asLong();
    }

    protected void decideQuote(long quoteId, String action, long requestId) throws Exception {
        mvc.perform(post("/api/internal/repair-quotes/{id}/{action}", quoteId, action).header("X-Internal-Api-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repairRequestId\":%d,\"customerAccountId\":%d,\"note\":\"ok\"}".formatted(requestId, BUYER)))
                .andExpect(status().isOk());
    }

    protected long approvedQuote(long requestId, long basePaise) throws Exception {
        long id = repairQuote(requestId, basePaise);
        decideQuote(id, "approve", requestId);
        return id;
    }

    protected ResultActions payQuote(long buyer, long quoteId, String method, String outcome, long amountPaise)
            throws Exception {
        return postJson("/api/payments/simulate", Tokens.customer(buyer),
                "{\"repairQuoteId\":%d,\"method\":\"%s\",\"outcome\":\"%s\",\"amountPaise\":%d}"
                        .formatted(quoteId, method, outcome, amountPaise));
    }

    /** Total the customer owes for a quote (base + customer fee). */
    protected long quoteTotal(long quoteId) {
        return quotes.findById(quoteId).orElseThrow().getTotalPayablePaise();
    }
}
