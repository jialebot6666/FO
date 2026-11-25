package com.fo.addon.elytra.hell;

import com.fo.addon.elytra.hell.HellFlightLogic.Vec;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HellFlightLogicTest {
    @Test
    void supplyNeedsMatchXpModeTargets() {
        HellFlightLogic.Needs needs = HellFlightLogic.needs(1408, 128, 32, 1, 23, 3, 1, 2);
        assertEquals(64, needs.fireworks());
        assertEquals(64, needs.xp());
        assertEquals(32, needs.food());
        assertEquals(1, needs.totems());
        assertTrue(needs.any());
        assertFalse(HellFlightLogic.needs(1472, 192, 64, 2, 23, 3, 1, 2).any());
    }

    @Test
    void landingThresholdsAndArrivalAreStable() {
        assertTrue(HellFlightLogic.shouldLandForSupply(191, 32, 16, 1, false));
        assertTrue(HellFlightLogic.shouldLandForSupply(192, 31, 16, 1, false));
        assertTrue(HellFlightLogic.shouldLandForSupply(192, 32, 15, 1, false));
        assertTrue(HellFlightLogic.shouldLandForSupply(192, 32, 16, 0, false));
        assertTrue(HellFlightLogic.shouldLandForSupply(192, 32, 16, 1, true));
        assertFalse(HellFlightLogic.shouldLandForSupply(192, 32, 16, 1, false));
        assertTrue(HellFlightLogic.arrived(3800.9));
        assertFalse(HellFlightLogic.arrived(3801.0));
    }

    @Test
    void safetyAndFireworkRules() {
        assertTrue(HellFlightLogic.lowHealthExit(3.4, 1));
        assertFalse(HellFlightLogic.lowHealthExit(3.4, 2));
        assertFalse(HellFlightLogic.lowHealthExit(3.5, 1));
        assertTrue(HellFlightLogic.shouldUseFirework(2.24, 1.5, false, true, true, true));
        assertFalse(HellFlightLogic.shouldUseFirework(2.25, 1.5, false, true, true, true));
        assertFalse(HellFlightLogic.shouldUseFirework(1.0, 1.5, true, true, true, true));
        assertFalse(HellFlightLogic.shouldUseFirework(1.0, 1.5, false, false, true, true));
    }

    @Test
    void containerSlotMappingMatchesVanillaLayout() {
        assertEquals(81, HellFlightLogic.hotbarScreenSlot(6, 0));
        assertEquals(89, HellFlightLogic.hotbarScreenSlot(6, 8));
        assertEquals(81, HellFlightLogic.inventoryScreenSlot(6, 0));
        assertEquals(54, HellFlightLogic.inventoryScreenSlot(6, 9));
        assertEquals(80, HellFlightLogic.inventoryScreenSlot(6, 35));
        assertEquals(54, HellFlightLogic.hotbarScreenSlot(3, 0));
        assertEquals(27, HellFlightLogic.inventoryScreenSlot(3, 9));
    }

    @Test
    void supplyBoxSelectionIncludesXpAndNeverReusesBox() {
        List<HellFlightLogic.BoxCounts> boxes = Arrays.asList(
            new HellFlightLogic.BoxCounts(64, 0, 0, 0),
            null,
            new HellFlightLogic.BoxCounts(0, 64, 16, 1),
            new HellFlightLogic.BoxCounts(64, 64, 16, 1)
        );
        List<Integer> selected = HellFlightLogic.chooseSupplyBoxes(new HellFlightLogic.Needs(100, 100, 20, 1), boxes);
        assertEquals(List.of(3, 2, 0), selected);
        assertEquals(3, selected.stream().distinct().count());
    }

    @Test
    void anglesAndFormattingAreUsable() {
        assertEquals(0, HellFlightLogic.yawTo(new Vec(0, 0, 0), new Vec(0, 0, 1)), 0.001);
        assertEquals(-90, HellFlightLogic.yawTo(new Vec(0, 0, 0), new Vec(1, 0, 0)), 0.001);
        assertEquals(-35.264389, HellFlightLogic.pitchTo(new Vec(0, 0, 0), new Vec(1, 1, 1)), 0.001);
        assertEquals("1.5 千格", HellFlightLogic.formatDistance(1500));
        assertEquals("1 分 5 秒", HellFlightLogic.formatDuration(65 * 20));
    }

    @Test
    void landingSafetyRequiresEveryGuard() {
        assertTrue(HellFlightLogic.landingColumnSafe(true, true, true, true, true, true));
        assertFalse(HellFlightLogic.landingColumnSafe(true, false, true, true, true, true));
        assertFalse(HellFlightLogic.landingColumnSafe(true, true, false, true, true, true));
        assertFalse(HellFlightLogic.landingColumnSafe(true, true, true, true, false, true));
        assertFalse(HellFlightLogic.landingColumnSafe(true, true, true, true, true, false));
    }
}
