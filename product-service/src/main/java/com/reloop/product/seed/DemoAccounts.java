package com.reloop.product.seed;

import com.reloop.product.dto.auth.CustomerRegistrationRequest;
import com.reloop.product.dto.auth.RepairerRegistrationRequest;
import com.reloop.product.enums.RepairMethod;
import java.util.List;
import java.util.Set;

/**
 * TEMPORARY, LOCAL-ONLY demo credentials (documented in docs/AUTH.md).
 * They are created only when {@code reloop.seed.enabled=true}. Never enable seeding in a
 * shared or deployed environment.
 */
public final class DemoAccounts {

    public static final String CUSTOMER_EMAIL = "customer.demo@reloop.local";
    public static final String CUSTOMER_PASSWORD = "ReLoopDemo#Cust2026";

    /** Second demo customer (seller in Surat, owns a demo store). */
    public static final String KARAN_EMAIL = "karan.demo@reloop.local";
    public static final String KARAN_PASSWORD = "ReLoopDemo#Karan2026";

    /** Third demo customer (seller in Vadodara and Rajkot). */
    public static final String NISHA_EMAIL = "nisha.demo@reloop.local";
    public static final String NISHA_PASSWORD = "ReLoopDemo#Nisha2026";

    public static final String REPAIRER_EMAIL = "repairer.demo@reloop.local";
    public static final String REPAIRER_PASSWORD = "ReLoopDemo#Rep2026";

    private DemoAccounts() {
    }

    public static CustomerRegistrationRequest customer() {
        return new CustomerRegistrationRequest(
                "Asha Patel", CUSTOMER_EMAIL, "+919800000001", CUSTOMER_PASSWORD,
                "12 Riverfront Road, Navrangpura", "Ahmedabad", "380009", "Gujarat");
    }

    public static CustomerRegistrationRequest karan() {
        return new CustomerRegistrationRequest(
                "Karan Desai", KARAN_EMAIL, "+919800000003", KARAN_PASSWORD,
                "21 Ghod Dod Road", "Surat", "395007", "Gujarat");
    }

    public static CustomerRegistrationRequest nisha() {
        return new CustomerRegistrationRequest(
                "Nisha Trivedi", NISHA_EMAIL, "+919800000004", NISHA_PASSWORD,
                "8 Race Course Circle, Alkapuri", "Vadodara", "390007", "Gujarat");
    }

    /** All demo customers, in seeding order. */
    public static List<CustomerRegistrationRequest> customers() {
        return List.of(customer(), karan(), nisha());
    }

    public static RepairerRegistrationRequest repairer() {
        return new RepairerRegistrationRequest(
                "Ravi Mehta", REPAIRER_EMAIL, "+919800000002", REPAIRER_PASSWORD,
                "Mehta Fix-It Workshop (Demo)",
                List.of("Mobile phones", "Laptops", "Small appliances"),
                "5 CG Road, Ellisbridge", "Ahmedabad", "380006",
                23.0225, 72.5714,
                "Mon-Sat 10:00-19:00",
                Set.of(RepairMethod.IN_PERSON, RepairMethod.SEND_ITEM, RepairMethod.PICKUP_DROPOFF));
    }
}
