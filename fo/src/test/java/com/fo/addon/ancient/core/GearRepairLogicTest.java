package com.fo.addon.ancient.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 耐久阈值/目标/最低安全线的边界。 */
class GearRepairLogicTest {

    @Test
    void 剩余百分比与点数() {
        assertEquals(50.0, GearRepairLogic.remainingPercent(100, 50), 1e-9);
        assertEquals(0.0, GearRepairLogic.remainingPercent(100, 120), 1e-9);
        assertEquals(100.0, GearRepairLogic.remainingPercent(0, 5), 1e-9);
        assertEquals(8, GearRepairLogic.remaining(100, 92));
        assertEquals(0, GearRepairLogic.remaining(100, 200));
    }

    @Test
    void 二十百分比是严格小于的边界() {
        // 432 最大耐久：剩 87 = 20.14%，不触发；剩 86 = 19.9%，触发
        assertFalse(GearRepairLogic.needsRepair(432, 345, 20.0));
        assertTrue(GearRepairLogic.needsRepair(432, 346, 20.0));
    }

    @Test
    void 九十百分比是大于等于的停止边界() {
        assertTrue(GearRepairLogic.repairDone(100, 10, 90.0));
        assertFalse(GearRepairLogic.repairDone(100, 11, 90.0));
    }

    @Test
    void 低于八点才算低于安全线() {
        assertFalse(GearRepairLogic.belowSafety(100, 92, 8)); // 剩 8
        assertTrue(GearRepairLogic.belowSafety(100, 93, 8));  // 剩 7
    }

    @Test
    void 决策顺序先保命再修复() {
        // 低于安全线：即使有经验修补也换备用
        assertEquals(GearRepairLogic.Action.REPLACE_SPARE,
            GearRepairLogic.decide(100, 95, 20, 8, true, true, true));
        // 低于安全线且没备用：暂停
        assertEquals(GearRepairLogic.Action.PAUSE,
            GearRepairLogic.decide(100, 95, 20, 8, true, false, true));
        // 需要修、有经验修补、有瓶：修复
        assertEquals(GearRepairLogic.Action.REPAIR,
            GearRepairLogic.decide(100, 85, 20, 8, true, false, true));
        // 需要修但没经验修补：换备用
        assertEquals(GearRepairLogic.Action.REPLACE_SPARE,
            GearRepairLogic.decide(100, 85, 20, 8, false, true, true));
        // 需要修、有经验修补但没瓶：暂停
        assertEquals(GearRepairLogic.Action.PAUSE,
            GearRepairLogic.decide(100, 85, 20, 8, true, false, false));
        // 不需要修：不动作
        assertEquals(GearRepairLogic.Action.NONE,
            GearRepairLogic.decide(100, 50, 20, 8, true, false, true));
        // 不可损坏：不动作
        assertEquals(GearRepairLogic.Action.NONE,
            GearRepairLogic.decide(0, 0, 20, 8, false, false, false));
    }
}
