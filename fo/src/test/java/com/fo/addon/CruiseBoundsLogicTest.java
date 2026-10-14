package com.fo.addon;

import com.fo.addon.utils.CruiseBoundsLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 巡航高度区间（自动调界）单测（对齐 Pitch40Util#resetBounds） */
class CruiseBoundsLogicTest {

    // ===== 上界 = 当前高度 - 5 =====

    @Test
    void upperIsFiveBelowCurrentY() {
        assertEquals(225.0, CruiseBoundsLogic.upperFrom(230.0), 1e-9);
        assertEquals(175.0, CruiseBoundsLogic.upperFrom(180.0), 1e-9);
    }

    @Test
    void upperIsClampedToWorldSafetyCeiling() {
        // 抬到世界外没有意义：夹在 300（建筑上限 320 留 20 格余量）
        assertEquals(CruiseBoundsLogic.MAX_UPPER, CruiseBoundsLogic.upperFrom(500.0), 1e-9);
        assertEquals(300.0, CruiseBoundsLogic.upperFrom(305.0), 1e-9);
        assertEquals(299.0, CruiseBoundsLogic.upperFrom(304.0), 1e-9);
    }

    // ===== 下界 = 上界 - 区间间隔 =====

    @Test
    void lowerIsUpperMinusGap() {
        assertEquals(165.0, CruiseBoundsLogic.lowerFrom(225.0, 60.0), 1e-9);
        assertEquals(185.0, CruiseBoundsLogic.lowerFrom(225.0, 40.0), 1e-9);
    }

    // ===== 掉到下界以下 margin 格 → 需要重置区间 =====

    @Test
    void resetOnFallOnlyWhenBelowLowerMinusMargin() {
        double lower = 180.0;
        double margin = CruiseBoundsLogic.FALL_MARGIN;

        assertFalse(CruiseBoundsLogic.shouldResetOnFall(200.0, lower, margin));
        assertFalse(CruiseBoundsLogic.shouldResetOnFall(180.0, lower, margin));
        assertFalse(CruiseBoundsLogic.shouldResetOnFall(171.0, lower, margin));
        // 边界：恰好等于 lower - margin 即触发
        assertTrue(CruiseBoundsLogic.shouldResetOnFall(170.0, lower, margin));
        assertTrue(CruiseBoundsLogic.shouldResetOnFall(150.0, lower, margin));
    }

    // ===== 重置后区间落在玩家下方（这样才能爬回来） =====

    @Test
    void resetPutsBandBelowPlayer() {
        double y = 200.0;
        double gap = 60.0;
        double upper = CruiseBoundsLogic.upperFrom(y);
        double lower = CruiseBoundsLogic.lowerFrom(upper, gap);

        assertEquals(195.0, upper, 1e-9);
        assertEquals(135.0, lower, 1e-9);
        assertTrue(upper < y, "重置后上界应在当前高度之下");
        assertTrue(lower < upper, "下界应低于上界");
    }
}
