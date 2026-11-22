package com.fo.addon.ancient.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 竖井列：绝不取箱子正上方、四个方向、按距离排序。 */
class ShaftColumnLogicTest {

    @Test
    void 候选列绝不含箱子正上方() {
        List<ShaftColumnLogic.Column> cols = ShaftColumnLogic.candidates(100, 200, 2, 4);
        assertEquals(12, cols.size()); // 3 个偏移 × 4 个方向
        for (ShaftColumnLogic.Column c : cols) {
            assertFalse(c.x() == 100 && c.z() == 200, "不允许箱子正上方");
            assertFalse(ShaftColumnLogic.isAboveChest(100, 200, c));
        }
    }

    @Test
    void 偏移范围正确且四个方向都有() {
        List<ShaftColumnLogic.Column> cols = ShaftColumnLogic.candidates(0, 0, 2, 4);
        assertTrue(cols.contains(new ShaftColumnLogic.Column(0, -2, 2)));
        assertTrue(cols.contains(new ShaftColumnLogic.Column(0, 2, 2)));
        assertTrue(cols.contains(new ShaftColumnLogic.Column(-2, 0, 2)));
        assertTrue(cols.contains(new ShaftColumnLogic.Column(2, 0, 2)));
        assertTrue(cols.contains(new ShaftColumnLogic.Column(0, -4, 4)));
    }

    @Test
    void 井口四项全部满足才可用() {
        assertTrue(ShaftColumnLogic.surfaceOk(true, true, true, false));
        assertFalse(ShaftColumnLogic.surfaceOk(false, true, true, false));
        assertFalse(ShaftColumnLogic.surfaceOk(true, false, true, false));
        assertFalse(ShaftColumnLogic.surfaceOk(true, true, false, false));
        assertFalse(ShaftColumnLogic.surfaceOk(true, true, true, true));
    }

    @Test
    void 按距离由近到远排序() {
        List<ShaftColumnLogic.Column> cols = ShaftColumnLogic.candidates(0, 0, 2, 2);
        List<ShaftColumnLogic.Column> sorted = ShaftColumnLogic.sortedByDistance(cols, 10, 0);
        assertEquals(new ShaftColumnLogic.Column(2, 0, 2), sorted.get(0)); // 东侧最近
    }
}
