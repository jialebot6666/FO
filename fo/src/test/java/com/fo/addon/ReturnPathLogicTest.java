package com.fo.addon;

import com.fo.addon.utils.ReturnPathLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 返回起飞点判定逻辑单测（对齐 Ying StorageReturn 语义） */
class ReturnPathLogicTest {

    // ===== shouldRepath =====

    @Test
    void shouldRepath_whenIntervalElapsed() {
        // 距上次重发 >= 20 tick → 重发
        assertTrue(ReturnPathLogic.shouldRepath(20, 0, true));
        assertTrue(ReturnPathLogic.shouldRepath(40, 20, true));
        assertTrue(ReturnPathLogic.shouldRepath(100, 80, true));
    }

    @Test
    void shouldRepath_whenNotPathing() {
        // 即使间隔未到，只要当前没在寻路（上次寻路失败/结束）→ 重发
        assertTrue(ReturnPathLogic.shouldRepath(5, 0, false));
        assertTrue(ReturnPathLogic.shouldRepath(19, 0, false));
        assertTrue(ReturnPathLogic.shouldRepath(0, 0, false));
    }

    @Test
    void shouldRepath_whenWithinIntervalAndPathing() {
        // 间隔未到且仍在寻路 → 不重发（避免刷屏寻路请求）
        assertFalse(ReturnPathLogic.shouldRepath(0, 0, true));
        assertFalse(ReturnPathLogic.shouldRepath(10, 0, true));
        assertFalse(ReturnPathLogic.shouldRepath(19, 0, true));
    }

    // ===== timedOut =====

    @Test
    void timedOut_after600Ticks() {
        assertTrue(ReturnPathLogic.timedOut(601));
        assertTrue(ReturnPathLogic.timedOut(1200));
        assertTrue(ReturnPathLogic.timedOut(ReturnPathLogic.TIMEOUT_TICKS + 1));
    }

    @Test
    void notTimedOut_atOrBefore600Ticks() {
        assertFalse(ReturnPathLogic.timedOut(0));
        assertFalse(ReturnPathLogic.timedOut(599));
        assertFalse(ReturnPathLogic.timedOut(600));
        assertFalse(ReturnPathLogic.timedOut(ReturnPathLogic.TIMEOUT_TICKS));
    }

    // ===== 常量一致性 =====

    @Test
    void constantsMatchYingSemantics() {
        // Ying StorageReturn: REPATH_INTERVAL=20, TIMEOUT_TICKS=600
        assertEquals(20, ReturnPathLogic.REPATH_INTERVAL);
        assertEquals(600, ReturnPathLogic.TIMEOUT_TICKS);
    }
}
