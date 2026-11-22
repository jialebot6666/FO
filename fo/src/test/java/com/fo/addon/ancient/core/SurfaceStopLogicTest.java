package com.fo.addon.ancient.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 地表停靠触发条件。 */
class SurfaceStopLogicTest {

    @Test
    void 六十四才存盒六十三不存() {
        assertFalse(SurfaceStopLogic.shouldPack(63, 64, false));
        assertTrue(SurfaceStopLogic.shouldPack(64, 64, false));
        assertTrue(SurfaceStopLogic.shouldPack(1, 64, true));
    }

    @Test
    void 任一装备低耐久就修() {
        assertFalse(SurfaceStopLogic.shouldRepair(false, false));
        assertTrue(SurfaceStopLogic.shouldRepair(true, false));
        assertTrue(SurfaceStopLogic.shouldRepair(false, true));
    }

    @Test
    void 任一补给不足就补给() {
        assertFalse(SurfaceStopLogic.shouldSupply(false, false, false));
        assertTrue(SurfaceStopLogic.shouldSupply(true, false, false));
        assertTrue(SurfaceStopLogic.shouldSupply(false, true, false));
        assertTrue(SurfaceStopLogic.shouldSupply(false, false, true));
    }

    @Test
    void 汇总动作可以同时发生() {
        SurfaceStopLogic.Needs needs = SurfaceStopLogic.evaluate(
            64, 64, false, true, true, true, false, false);
        assertTrue(needs.pack());
        assertTrue(needs.supply());
        assertTrue(needs.repair());
        assertTrue(needs.any());

        assertFalse(SurfaceStopLogic.evaluate(10, 64, false, false, false, false, false, false).any());
    }
}
