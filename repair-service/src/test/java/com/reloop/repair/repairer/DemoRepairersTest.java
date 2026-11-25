package com.reloop.repair.repairer;

import static org.assertj.core.api.Assertions.assertThat;

import com.reloop.repair.seed.DemoRepairers;
import com.reloop.repair.service.GeoUtils;
import com.reloop.repair.service.ProfileRequestRules;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The demo workshops must pass the same validation as {@code POST /api/repairers} and be clearly labelled. */
class DemoRepairersTest {

    @Test
    void demoWorkshopsAreValidLabelledAndInGujarat() {
        Set<Long> accountIds = new HashSet<>();
        Set<String> cities = new HashSet<>();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            for (DemoRepairers.DemoWorkshop w : DemoRepairers.WORKSHOPS) {
                var p = w.profile();
                assertThat(validator.validate(p)).as(p.businessName()).isEmpty();
                assertThat(ProfileRequestRules.check(p, null)).as(p.businessName()).isEmpty();
                assertThat(p.businessName()).endsWith("(Demo)");
                assertThat(w.accountExternalId()).isNegative();
                assertThat(accountIds.add(w.accountExternalId())).isTrue();
                assertThat(GeoUtils.inIndia(p.latitude(), p.longitude())).isTrue();
                assertThat(p.latitude()).isBetween(20.0, 24.8);
                assertThat(p.longitude()).isBetween(68.0, 74.6);
                assertThat(w.reviews()).allSatisfy(r -> assertThat(r.rating()).isBetween(1, 5));
                cities.add(p.city());
            }
        }
        assertThat(cities).contains("Ahmedabad", "Surat", "Vadodara", "Rajkot", "Gandhinagar");
    }
}
