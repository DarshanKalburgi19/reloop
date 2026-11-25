package com.reloop.repair.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.reloop.repair.dto.inventory.CreateSparePartRequest;
import com.reloop.repair.dto.inventory.UsePartRequest;
import com.reloop.repair.entity.RepairRequest;
import com.reloop.repair.entity.RepairerProfile;
import com.reloop.repair.enums.DeliveryMode;
import com.reloop.repair.enums.RepairServiceCategory;
import com.reloop.repair.enums.RepairStatus;
import com.reloop.repair.enums.SparePartCategory;
import com.reloop.repair.exception.InsufficientStockException;
import com.reloop.repair.repository.InventoryMovementRepository;
import com.reloop.repair.repository.RepairRequestRepository;
import com.reloop.repair.repository.RepairerProfileRepository;
import com.reloop.repair.repository.SparePartRepository;
import com.reloop.repair.security.AuthenticatedAccount;
import com.reloop.repair.service.InventoryService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;

/**
 * Many simultaneous "use 1 unit" calls against a part with only a few units: the row lock serialises them, so the
 * quantity ends at exactly 0 (never negative) and the ledger matches the units actually taken.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:inventory-concurrency-test;DB_CLOSE_DELAY=-1")
class InventoryConcurrencyTest {

    private static final int UNITS = 5;
    private static final int CALLERS = 12;

    @Autowired
    private InventoryService service;
    @Autowired
    private RepairerProfileRepository profiles;
    @Autowired
    private RepairRequestRepository requests;
    @Autowired
    private SparePartRepository parts;
    @Autowired
    private InventoryMovementRepository movements;

    @Test
    void concurrentUsageNeverOversellsTheLastUnits() throws Exception {
        RepairerProfile profile = new RepairerProfile();
        profile.setAccountExternalId(8801L);
        profile.setBusinessName("Rush Hour Repairs");
        profile.setCity("Ahmedabad");
        profile.setAcceptsInPerson(true);
        profile = profiles.saveAndFlush(profile);

        RepairRequest repair = new RepairRequest();
        repair.setRequestNumber("REQ-CONC-1");
        repair.setCustomerAccountId(1L);
        repair.setRepairerProfile(profile);
        repair.setDeliveryMode(DeliveryMode.IN_PERSON);
        repair.setItemCategory(RepairServiceCategory.MOBILE);
        repair.setItemDescription("Pixel 7");
        repair.setStatus(RepairStatus.IN_PROGRESS);
        repair = requests.saveAndFlush(repair);

        AuthenticatedAccount owner = new AuthenticatedAccount(8801L, "rush@reloop.test", AuthenticatedAccount.REPAIRER);
        long partId = service.create(owner, new CreateSparePartRequest("Pixel 7 battery", SparePartCategory.BATTERY,
                null, null, new BigDecimal("700.00"), UNITS, 1)).id();
        long repairId = repair.getId();

        ExecutorService pool = Executors.newFixedThreadPool(CALLERS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < CALLERS; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    service.use(owner, partId, new UsePartRequest(repairId, 1, null));
                    return "ok";
                } catch (InsufficientStockException e) {
                    return "insufficient";
                } catch (PessimisticLockingFailureException e) {
                    return "lock-timeout";
                }
            }));
        }
        start.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> f : results) {
            outcomes.add(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        long succeeded = outcomes.stream().filter("ok"::equals).count();
        int finalQuantity = parts.findById(partId).orElseThrow().getQuantity();

        assertThat(succeeded).isLessThanOrEqualTo(UNITS);
        assertThat(finalQuantity).isEqualTo(UNITS - succeeded).isGreaterThanOrEqualTo(0);
        assertThat(movements.findByRepairRequest_Id(repairId)).hasSize((int) succeeded);
        // with a lock wait well under H2's timeout every unit is taken and the rest are refused
        if (!outcomes.contains("lock-timeout")) {
            assertThat(succeeded).isEqualTo(UNITS);
            assertThat(outcomes.stream().filter("insufficient"::equals).count()).isEqualTo(CALLERS - UNITS);
        }
    }
}
