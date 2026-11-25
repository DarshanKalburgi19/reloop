package com.reloop.repair.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.reloop.repair.dto.repairer.RepairerProfileRequest;
import com.reloop.repair.dto.repairer.ServiceOfferingRequest;
import com.reloop.repair.dto.repairer.WorkingHoursRequest;
import com.reloop.repair.enums.AvailabilityStatus;
import com.reloop.repair.enums.PriceType;
import com.reloop.repair.enums.RepairMethod;
import com.reloop.repair.enums.RepairServiceCategory;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProfileRequestRulesTest {

    private static RepairerProfileRequest request(double lat, double lng, List<ServiceOfferingRequest> services,
                                                  List<WorkingHoursRequest> hours) {
        return new RepairerProfileRequest("Rules Workshop", "A description that is long enough.",
                Set.of(RepairServiceCategory.MOBILE), List.of("Screens"), services, null, "1 Main Road", "Surat",
                "395001", lat, lng, hours, Set.of(RepairMethod.IN_PERSON), AvailabilityStatus.AVAILABLE, null);
    }

    private static ServiceOfferingRequest service(Long id, String name, RepairServiceCategory category) {
        return new ServiceOfferingRequest(id, name, null, category, PriceType.FIXED, new BigDecimal("500"), 1);
    }

    private static final List<WorkingHoursRequest> HOURS = List.of(new WorkingHoursRequest(DayOfWeek.MONDAY, "10:00", "18:00"));

    @Test
    void validRequestHasNoErrors() {
        assertThat(ProfileRequestRules.check(request(21.17, 72.83,
                List.of(service(null, "Screen", RepairServiceCategory.MOBILE)), HOURS), null)).isEmpty();
    }

    @Test
    void locationMustBeInIndiaAndSwapsAreDetected() {
        assertThat(ProfileRequestRules.check(request(72.83, 21.17,
                List.of(service(null, "Screen", RepairServiceCategory.MOBILE)), HOURS), null).get("latitude"))
                .contains("swapped");
        assertThat(ProfileRequestRules.check(request(40.71, -74.0,
                List.of(service(null, "Screen", RepairServiceCategory.MOBILE)), HOURS), null).get("latitude"))
                .contains("India");
    }

    @Test
    void serviceIdsMustBelongToTheWorkshop() {
        var req = request(21.17, 72.83, List.of(service(7L, "Screen", RepairServiceCategory.MOBILE),
                service(7L, "Battery", RepairServiceCategory.MOBILE), service(99L, "Port", RepairServiceCategory.MOBILE)), HOURS);
        var errors = ProfileRequestRules.check(req, Set.of(7L, 8L));
        assertThat(errors).containsEntry("services[1].id", "duplicate service id")
                .containsEntry("services[2].id", "unknown service id for this workshop")
                .doesNotContainKey("services[0].id");
        assertThat(ProfileRequestRules.check(req, null)).containsKey("services[0].id");
    }

    @Test
    void serviceCategoryAndHoursRules() {
        var errors = ProfileRequestRules.check(request(21.17, 72.83,
                List.of(service(null, "Cycle", RepairServiceCategory.BICYCLE)),
                List.of(new WorkingHoursRequest(DayOfWeek.MONDAY, "18:00", "10:00"),
                        new WorkingHoursRequest(DayOfWeek.MONDAY, "09:00", "10:00"))), null);
        assertThat(errors).containsKeys("services[0].category", "workingHours[0].closes", "workingHours[1].day");
    }

    @Test
    void citiesAreNormalised() {
        assertThat(ProfileRequestRules.normaliseCity("  ahmedabad ")).isEqualTo("Ahmedabad");
        assertThat(ProfileRequestRules.normaliseCity("VALLABH   VIDYANAGAR")).isEqualTo("Vallabh Vidyanagar");
        assertThat(ProfileRequestRules.normaliseCity("McLeod Ganj")).isEqualTo("McLeod Ganj");
    }
}
