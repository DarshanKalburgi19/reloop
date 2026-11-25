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

    public static final String REPAIRER_EMAIL = "repairer.demo@reloop.local";
    public static final String REPAIRER_PASSWORD = "ReLoopDemo#Rep2026";

    private DemoAccounts() {
    }

    public static CustomerRegistrationRequest customer() {
        return new CustomerRegistrationRequest(
                "Asha Patel", CUSTOMER_EMAIL, "+919800000001", CUSTOMER_PASSWORD,
                "12 Riverfront Road, Navrangpura", "Ahmedabad", "380009", "Gujarat");
    }

    public static RepairerRegistrationRequest repairer() {
        return new RepairerRegistrationRequest(
                "Ravi Mehta", REPAIRER_EMAIL, "+919800000002", REPAIRER_PASSWORD,
                "Mehta Fix-It Workshop",
                List.of("Mobile phones", "Laptops", "Small appliances"),
                "5 CG Road, Ellisbridge", "Ahmedabad", "380006",
                23.0225, 72.5714,
                "Mon-Sat 10:00-19:00",
                Set.of(RepairMethod.IN_PERSON, RepairMethod.SEND_ITEM, RepairMethod.PICKUP_DROPOFF));
    }
}
