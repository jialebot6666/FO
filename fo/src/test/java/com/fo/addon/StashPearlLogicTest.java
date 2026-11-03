package com.fo.addon;

import com.fo.addon.utils.StashPearlLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 珍珠补货 / 坐标 / 潜影盒判定（V6.7 自动搬运）。 */
class StashPearlLogicTest {

    @Test
    void 低于阈值才补货() {
        assertTrue(StashPearlLogic.needsRestock(3, 4));
        assertFalse(StashPearlLogic.needsRestock(4, 4));
        assertFalse(StashPearlLogic.needsRestock(16, 4));
    }

    @Test
    void 缺口精确补到目标() {
        // 手上 3 颗 → 补 13 颗到 16（不是整堆搬 16 颗）
        assertEquals(13, StashPearlLogic.missingToTarget(3, 4, 16));
        // 手上 0 颗 → 补 16
        assertEquals(16, StashPearlLogic.missingToTarget(0, 4, 16));
        // 手上 6 颗已高于阈值 → 不补
        assertEquals(0, StashPearlLogic.missingToTarget(6, 4, 16));
    }

    @Test
    void 缺口不会为负() {
        assertEquals(0, StashPearlLogic.missingToTarget(20, 4, 16));
    }

    @Test
    void 本次取货受箱内堆大小限制() {
        assertEquals(13, StashPearlLogic.takeNow(13, 16));
        assertEquals(5, StashPearlLogic.takeNow(13, 5));
        assertEquals(0, StashPearlLogic.takeNow(0, 16));
        assertEquals(0, StashPearlLogic.takeNow(13, 0));
    }

    @Test
    void 潜影盒ID判定覆盖全部染色() {
        assertTrue(StashPearlLogic.isShulkerBoxId("minecraft:shulker_box"));
        assertTrue(StashPearlLogic.isShulkerBoxId("minecraft:white_shulker_box"));
        assertTrue(StashPearlLogic.isShulkerBoxId("minecraft:light_blue_shulker_box"));
        assertFalse(StashPearlLogic.isShulkerBoxId("minecraft:shulker_shell"));
        assertFalse(StashPearlLogic.isShulkerBoxId("minecraft:chest"));
        assertFalse(StashPearlLogic.isShulkerBoxId(null));
    }

    @Test
    void 坐标格式化与解析往返() {
        String s = StashPearlLogic.formatPos(12, 64, -30);
        assertEquals("12 64 -30", s);
        int[] xyz = StashPearlLogic.parsePos(s);
        assertEquals(12, xyz[0]);
        assertEquals(64, xyz[1]);
        assertEquals(-30, xyz[2]);
    }

    @Test
    void 坐标解析容错() {
        assertNull(StashPearlLogic.parsePos("12 64"));
        assertNull(StashPearlLogic.parsePos("a b c"));
        assertNull(StashPearlLogic.parsePos(null));
        int[] xyz = StashPearlLogic.parsePos("  1   2  3  ");
        assertEquals(1, xyz[0]);
        assertEquals(3, xyz[2]);
    }
}
