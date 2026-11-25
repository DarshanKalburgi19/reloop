package com.reloop.repair.repairrequest;

import com.reloop.repair.client.CreateQuoteCommand;
import com.reloop.repair.client.QuoteView;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import javax.crypto.SecretKey;

/** Tokens, a complete workshop profile and fake order-service quotes for the repair request tests. */
final class RepairFixtures {

    static final String SECRET = "test-only-jwt-secret-0123456789-abcdefghijklmnop";
    static final String ISSUER = "reloop-product-service";
    static final String INTERNAL_KEY = "test-internal-key";
    static final BigDecimal TWO_PERCENT = new BigDecimal("0.0200");

    private RepairFixtures() {
    }

    static String customer(long accountId) {
        return bearer(accountId, "CUSTOMER");
    }

    static String repairer(long accountId) {
        return bearer(accountId, "REPAIRER");
    }

    private static String bearer(long accountId, String role) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder()
                .subject(Long.toString(accountId))
                .issuer(ISSUER)
                .claim("email", "user" + accountId + "@reloop.test")
                .claim("role", role)
                .issuedAt(Date.from(Instant.now().minusSeconds(5)))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(key)
                .compact();
    }

    /** Complete Ahmedabad workshop: MOBILE + LAPTOP, in-person and send-item. */
    static String workshop(String name) {
        return """
            {"businessName":"%s",
             "description":"Phone and laptop repairs near CG Road with genuine parts and warranty.",
             "categories":["MOBILE","LAPTOP"],
             "expertise":["Screen replacement","Micro-soldering"],
             "services":[{"name":"Phone screen replacement","category":"MOBILE","priceType":"STARTING_FROM","price":1499,"estimatedDays":1},
                         {"name":"Laptop OS reinstall","category":"LAPTOP","priceType":"FIXED","price":499}],
             "phone":"+919812345678",
             "addressLine":"12 CG Road, Navrangpura",
             "city":"ahmedabad",
             "pincode":"380009",
             "latitude":23.0276,
             "longitude":72.5577,
             "workingHours":[
               {"day":"MONDAY","opens":"10:00","closes":"19:00"},{"day":"TUESDAY","opens":"10:00","closes":"19:00"},
               {"day":"WEDNESDAY","opens":"10:00","closes":"19:00"},{"day":"THURSDAY","opens":"10:00","closes":"19:00"},
               {"day":"FRIDAY","opens":"10:00","closes":"19:00"},{"day":"SATURDAY","opens":"10:00","closes":"19:00"}],
             "repairMethods":["IN_PERSON","SEND_ITEM"],
             "availability":"AVAILABLE",
             "availabilityNote":"Same-day screen repairs"}
            """.formatted(name);
    }

    static String requestBody(long profileId, String category, String method) {
        return """
            {"repairerProfileId":%d,"category":"%s","itemDescription":"Samsung Galaxy S21",
             "problemDescription":"Screen cracked after a fall; touch stops working in the top half.",
             "imageUrls":["/demo-images/repairs/phone-cracked-screen.svg"],
             "deliveryMethod":"%s","customerNotes":"Evenings preferred"}
            """.formatted(profileId, category, method);
    }

    /** What order-service would answer: 2% customer fee and 2% commission on {@code base}. */
    static QuoteView quote(long id, CreateQuoteCommand cmd, int revision) {
        long fee = percent(cmd.baseAmountPaise());
        return new QuoteView(id, cmd.repairRequestId(), cmd.repairerProfileId(), cmd.customerAccountId(), revision, "SENT",
                cmd.baseAmountPaise(), TWO_PERCENT, fee, cmd.baseAmountPaise() + fee, TWO_PERCENT, fee,
                cmd.baseAmountPaise() - fee, "INR", cmd.summary(), LocalDateTime.now());
    }

    static QuoteView withStatus(QuoteView q, long id, String status) {
        return new QuoteView(id, q.repairRequestId(), q.repairerProfileId(), q.customerAccountId(), q.revision(), status,
                q.baseAmountPaise(), q.customerFeeRate(), q.customerFeePaise(), q.totalPayablePaise(),
                q.repairerCommissionRate(), q.repairerCommissionPaise(), q.repairerNetPaise(), q.currency(), q.summary(),
                q.sentAt());
    }

    private static long percent(long paise) {
        return BigDecimal.valueOf(paise).multiply(TWO_PERCENT).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
