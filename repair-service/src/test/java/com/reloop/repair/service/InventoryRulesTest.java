package com.reloop.repair.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reloop.repair.entity.InventoryMovement;
import com.reloop.repair.entity.SparePart;
import com.reloop.repair.enums.SparePartCategory;
import com.reloop.repair.enums.SparePartStatus;
import com.reloop.repair.enums.StockStatus;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Plain unit tests for the inventory rules (no Spring context). */
class InventoryRulesTest {

    private static SparePart part(int quantity, int threshold, long unitCostPaise) {
        SparePart p = new SparePart();
        p.setPartName("Battery");
        p.setQuantity(quantity);
        p.setLowStockThreshold(threshold);
        p.setUnitCostPaise(unitCostPaise);
        return p;
    }

    @Test
    void quantityCanNeverGoBelowZero() {
        SparePart p = part(2, 1, 100);

        assertThat(p.changeQuantity(-2)).isZero();
        assertThatThrownBy(() -> p.changeQuantity(-1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("negative");
        assertThat(p.getQuantity()).isZero();
    }

    @Test
    void quantityIsCappedToAvoidOverflow() {
        SparePart p = part(SparePart.MAX_QUANTITY, 1, 100);

        assertThatThrownBy(() -> p.changeQuantity(1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> p.changeQuantity(Integer.MAX_VALUE)).isInstanceOf(IllegalStateException.class);
        assertThat(p.getQuantity()).isEqualTo(SparePart.MAX_QUANTITY);
    }

    @ParameterizedTest
    @CsvSource({"0, 5, OUT_OF_STOCK, true", "5, 5, LOW_STOCK, true", "1, 5, LOW_STOCK, true", "6, 5, IN_STOCK, false",
                "0, 0, OUT_OF_STOCK, true", "1, 0, IN_STOCK, false"})
    void stockStatusFollowsTheThreshold(int quantity, int threshold, StockStatus expected, boolean low) {
        SparePart p = part(quantity, threshold, 100);

        assertThat(p.stockStatus()).isEqualTo(expected);
        assertThat(p.isLowStock()).isEqualTo(low);
    }

    @Test
    void discontinuedPartsNeverRaiseAnAlert() {
        SparePart p = part(0, 5, 100);
        assertThat(InventoryMapper.raisesAlert(p)).isTrue();
        assertThat(InventoryMapper.alertMessage(p)).isEqualTo("'Battery' is out of stock");

        p.setStatus(SparePartStatus.DISCONTINUED);
        assertThat(InventoryMapper.raisesAlert(p)).isFalse();
        assertThat(InventoryMapper.alertMessage(p)).isNull();
    }

    @Test
    void stockValueIsQuantityTimesUnitCostInPaise() {
        assertThat(part(9, 1, 40_000).stockValuePaise()).isEqualTo(360_000);
        assertThat(part(0, 1, 40_000).stockValuePaise()).isZero();
        assertThat(part(SparePart.MAX_QUANTITY, 1, 99_999_999_999L).stockValuePaise())
                .isEqualTo(99_999_999_999_000_000L);
    }

    @ParameterizedTest
    @CsvSource({
        "10, 10000, 10, 12000, 11000",   // equal lots: simple mean
        "0, 5000, 4, 7000, 7000",         // nothing on hand: the new price
        "3, 100, 1, 101, 100",            // 100.25 rounds down
        "1, 100, 1, 101, 101",            // 100.5 rounds half-up
        "2, 0, 2, 0, 0"})
    void weightedAverageUnitCost(int onHand, long onHandCost, int received, long receivedCost, long expected) {
        assertThat(InventoryService.weightedAverage(onHand, onHandCost, received, receivedCost)).isEqualTo(expected);
    }

    @Test
    void movementQuantityIsAbsoluteAndCostIsQuantityTimesUnitCost() {
        InventoryMovement m = new InventoryMovement();
        m.setQuantityChange(-3);
        m.setUnitCostPaise(25_000L);

        assertThat(m.getQuantity()).isEqualTo(3);
        assertThat(m.totalCostPaise()).isEqualTo(75_000);
    }

    @Test
    void generatedSkusUseTheCategoryPrefixAndAnUnambiguousAlphabet() {
        SkuGenerator generator = new SkuGenerator(new Random(42));
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String sku = generator.next(SparePartCategory.SCREEN_DISPLAY);
            assertThat(sku).matches("SCR-[2-9A-HJKMNP-Z]{8}");
            seen.add(sku);
        }
        assertThat(seen).hasSizeGreaterThan(195);
        assertThat(generator.next(null)).startsWith("OTH-");
    }

    @Test
    void skusAreStoredUpperCaseAndTrimmed() {
        assertThat(InventoryService.normaliseSku(" scr-ip11-blk ")).isEqualTo("SCR-IP11-BLK");
    }
}
