package com.fo.addon;

import com.fo.addon.elytra.core.SegFailWindow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Baritone 段失败计数窗口（{@link SegFailWindow}）的行为锁定。
 *
 * 这是移植时从 BaritoneHook 里抽出来的纯逻辑：Baritone 连续报「算不出路线」时，
 * 6 tick 窗口内的条数决定要不要重置鞘翅进程 —— 窗口边界差一格就会出现
 * 「该重置时没重置（原地绕圈）」或「不该重置时乱重置（正常飞行被打断）」。
 */
class SegFailWindowTest {

    @Test
    void freshWindowHasNoFailures() {
        SegFailWindow w = new SegFailWindow();
        assertEquals(0, w.count());
    }

    @Test
    void oneFailureCountsAsOne() {
        SegFailWindow w = new SegFailWindow();
        w.recordFailure();
        assertEquals(1, w.count());
    }

    @Test
    void failuresInsideWindowAccumulate() {
        SegFailWindow w = new SegFailWindow();
        w.recordFailure();
        for (int i = 0; i < 5; i++) {
            w.markTick();
            w.recordFailure();
        }
        assertEquals(6, w.count());
    }

    @Test
    void gapExactlyWindowStillCountsAsSameWindow() {
        // 边界：间隔恰好等于 WINDOW 时仍属于同一轮（只有严格大于才算过期）
        SegFailWindow w = new SegFailWindow();
        w.recordFailure();
        for (int i = 0; i < SegFailWindow.WINDOW; i++) w.markTick();
        assertEquals(1, w.count(), "间隔恰好 = WINDOW 时不该过期");

        w.recordFailure();
        assertEquals(2, w.count(), "间隔恰好 = WINDOW 时应继续累加");
    }

    @Test
    void gapBeyondWindowResetsCount() {
        SegFailWindow w = new SegFailWindow();
        w.recordFailure();
        for (int i = 0; i < SegFailWindow.WINDOW + 1; i++) w.markTick();
        assertEquals(0, w.count(), "间隔超过 WINDOW 后窗口过期，计数归零");

        w.recordFailure();
        assertEquals(1, w.count(), "过期后的第一条重新从 1 开始");
    }

    @Test
    void resetRequestIsConsumedOnceOnly() {
        SegFailWindow w = new SegFailWindow();
        w.recordFailure();
        assertTrue(w.consumeResetRequest(), "记到失败后应产生一次重置请求");
        assertFalse(w.consumeResetRequest(), "重置请求只能被取走一次");
    }

    @Test
    void noResetRequestBeforeAnyFailure() {
        SegFailWindow w = new SegFailWindow();
        assertFalse(w.consumeResetRequest());
    }

    @Test
    void clearResetsCountAndRequest() {
        SegFailWindow w = new SegFailWindow();
        w.recordFailure();
        w.clear();
        assertEquals(0, w.count());
        assertFalse(w.consumeResetRequest(), "clear 后不应残留重置请求");
    }

    @Test
    void clearKeepsTickClockRunning() {
        // 与原实现一致：clear 不清 tick 时钟，只清计数
        SegFailWindow w = new SegFailWindow();
        w.markTick();
        w.markTick();
        w.clear();
        w.recordFailure();
        assertEquals(1, w.count());
        for (int i = 0; i < SegFailWindow.WINDOW + 1; i++) w.markTick();
        assertEquals(0, w.count(), "clear 之后窗口判定仍按 tick 时钟走");
    }
}
