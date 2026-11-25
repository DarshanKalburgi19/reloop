package com.reloop.repair.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.reloop.repair.entity.InventoryMovement;
import com.reloop.repair.entity.RepairRequest;
import com.reloop.repair.entity.RepairStatusHistory;
import com.reloop.repair.entity.RepairerProfile;
import com.reloop.repair.entity.SparePart;
import com.reloop.repair.enums.DeliveryMode;
import com.reloop.repair.enums.InventoryMovementType;
import com.reloop.repair.enums.RepairServiceCategory;
import com.reloop.repair.enums.RepairStatus;
import com.reloop.repair.enums.StatusChangeActor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the repair-service schema on H2: profile uniqueness per account,
 * SKU uniqueness per repairer, the stock_quantity >= 0 CHECK constraint,
 * status-history queries and enum round-trips. Each test ends on its single
 * expected violation (a failed flush marks the transaction rollback-only).
 */
@SpringBootTest
@Transactional
class RepairSchemaTest {

    @Autowired
    private RepairerProfileRepository profiles;

    @Autowired
    private RepairRequestRepository requests;

    @Autowired
    private RepairStatusHistoryRepository history;

    @Autowired
    private SparePartRepository parts;

    @Autowired
    private InventoryMovementRepository movements;

    private RepairerProfile newProfile(Long accountExternalId, String businessName) {
        RepairerProfile profile = new RepairerProfile();
        profile.setAccountExternalId(accountExternalId);
        profile.setBusinessName(businessName);
        profile.setCity("Ahmedabad");
        profile.setAcceptsInPerson(true);
        return profile;
    }

    private SparePart newPart(RepairerProfile profile, String sku, int stock) {
        SparePart part = new SparePart();
        part.setRepairerProfile(profile);
        part.setSku(sku);
        part.setName("Part " + sku);
        part.setUnitCostPaise(450000L);
        part.setStockQuantity(stock);
        return part;
    }

    @Test
    void profileRequestHistoryRoundTrip() {
        RepairerProfile profile = profiles.saveAndFlush(newProfile(9001L, "GadgetFix Workshop"));
        assertNotNull(profile.getId());

        RepairRequest request = new RepairRequest();
        request.setRequestNumber("REQ-TEST-0001");
        request.setCustomerAccountId(101L);
        request.setRepairerProfile(profile);
        request.setDeliveryMode(DeliveryMode.IN_PERSON);
        request.setItemCategory(RepairServiceCategory.MOBILE);
        request.setItemDescription("Cracked screen");
        request = requests.saveAndFlush(request);

        RepairStatusHistory entry = new RepairStatusHistory();
        entry.setRepairRequest(request);
        entry.setFromStatus(null);
        entry.setToStatus(RepairStatus.REQUESTED);
        entry.setChangedBy(StatusChangeActor.CUSTOMER);
        entry.setChangedByAccountId(101L);
        history.saveAndFlush(entry);

        assertEquals(1, history.findByRepairRequest_IdOrderByCreatedAtAsc(request.getId()).size());
        assertEquals(1, requests.findByStatus(RepairStatus.REQUESTED).size());
        assertEquals(1, requests.findByCustomerAccountId(101L).size());
    }

    @Test
    void oneProfilePerAccountIsEnforced() {
        profiles.saveAndFlush(newProfile(9002L, "First Workshop"));

        assertThrows(DataIntegrityViolationException.class, () ->
                profiles.saveAndFlush(newProfile(9002L, "Second Workshop")));
    }

    @Test
    void duplicateSkuForSameRepairerIsRejected() {
        RepairerProfile profile = profiles.saveAndFlush(newProfile(9003L, "SpareHub"));
        parts.saveAndFlush(newPart(profile, "SCR-IP11-BLK", 5));

        assertThrows(DataIntegrityViolationException.class, () ->
                parts.saveAndFlush(newPart(profile, "SCR-IP11-BLK", 2)));
    }

    @Test
    void negativeStockIsRejectedByCheckConstraint() {
        RepairerProfile profile = profiles.saveAndFlush(newProfile(9004L, "BatteryWorks"));

        // stock_quantity >= 0 - inventory can never silently go negative.
        assertThrows(DataIntegrityViolationException.class, () ->
                parts.saveAndFlush(newPart(profile, "BAT-IP11-ORG", -1)));
    }

    @Test
    void inventoryMovementTracksStockLedger() {
        RepairerProfile profile = profiles.saveAndFlush(newProfile(9005L, "LedgerFix"));
        SparePart part = parts.saveAndFlush(newPart(profile, "CBL-USB-C", 10));

        InventoryMovement movement = new InventoryMovement();
        movement.setSparePart(part);
        movement.setMovementType(InventoryMovementType.RECEIVED);
        movement.setQuantityChange(+4);
        movement.setStockAfter(14);
        movements.saveAndFlush(movement);

        assertEquals(1, movements.findBySparePart_IdOrderByCreatedAt(part.getId()).size());
        assertEquals(InventoryMovementType.RECEIVED,
                movements.findBySparePart_IdOrderByCreatedAt(part.getId()).get(0).getMovementType());
        assertTrue(movements.findByRepairRequest_Id(999L).isEmpty());
    }
}
