package com.fo.addon.ancient.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 经验瓶补给边界：31/32/33、补到目标、本次上限。 */
class GearSupplyLogicTest {

    @Test
    void 最低数量是严格小于的边界() {
        assertTrue(GearSupplyLogic.needsSupply(31, 32));
        assertFalse(GearSupplyLogic.needsSupply(32, 32));
        assertFalse(GearSupplyLogic.needsSupply(33, 32));
    }

    @Test
    void 补到目标数量() {
        assertEquals(160, GearSupplyLogic.amountToWithdraw(32, 192));
        assertEquals(0, GearSupplyLogic.amountToWithdraw(200, 192));
        assertEquals(192, GearSupplyLogic.amountToWithdraw(-5, 192)); // 负数按 0 处理，补满目标
    }

    @Test
    void 本次投瓶上限与剩余瓶数取小() {
        assertEquals(100, GearSupplyLogic.bottlesToUse(100, 128, 0));
        assertEquals(28, GearSupplyLogic.bottlesToUse(100, 128, 100));
        assertEquals(0, GearSupplyLogic.bottlesToUse(100, 128, 128));
        assertEquals(0, GearSupplyLogic.bottlesToUse(0, 128, 10));
    }

    @Test
    void 低于最低瓶数不开始修复() {
        assertFalse(GearSupplyLogic.canStartRepair(31, 32));
        assertTrue(GearSupplyLogic.canStartRepair(32, 32));
    }

    @Test
    void 目标不得低于最低值() {
        assertEquals(64, GearSupplyLogic.effectiveTarget(64, 16));
        assertEquals(192, GearSupplyLogic.effectiveTarget(32, 192));
    }

    @Test
    void 主手镐模式使用副手投瓶() {
        assertTrue(GearSupplyLogic.useOffhandBottle(true));
        assertFalse(GearSupplyLogic.useOffhandBottle(false));
    }
}
