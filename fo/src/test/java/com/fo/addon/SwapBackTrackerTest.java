package com.fo.addon;

import com.fo.addon.utils.SwapBackTracker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SwapBackTracker 纯逻辑测试：杀戮光环「切换回来」状态机。
 *
 * <p>锁定彗星端（Meteor）官方 KillAura {@code swap-back} 的语义：
 * ① 仅本会话首次切换记录原槽位；② 会话结束按开关决定是否切回；③ 无论是否切回都复位。
 */
public class SwapBackTrackerTest {

    @Test
    void freshTrackerHasNoPendingRestore() {
        SwapBackTracker tracker = new SwapBackTracker();
        assertFalse(tracker.hasPendingRestore());
        assertEquals(SwapBackTracker.NO_SLOT, tracker.previousSlot());
        assertEquals(SwapBackTracker.NO_SLOT, tracker.completeSession(true));
    }

    @Test
    void firstSwapRecordsSlot() {
        SwapBackTracker tracker = new SwapBackTracker();
        tracker.recordBeforeSwap(3);
        assertTrue(tracker.hasPendingRestore());
        assertEquals(3, tracker.previousSlot());
    }

    /** 关键语义：会话期间反复切武器，记录不能被后续切换覆盖（否则会切到中途那把武器）。 */
    @Test
    void laterSwapsDoNotOverwriteRecordedSlot() {
        SwapBackTracker tracker = new SwapBackTracker();
        tracker.recordBeforeSwap(3);   // 切到武器前手上是 3 号槽
        tracker.recordBeforeSwap(7);   // 会话中再次切换，不应覆盖
        tracker.recordBeforeSwap(1);
        assertEquals(3, tracker.previousSlot());
        assertEquals(3, tracker.completeSession(true));
    }

    @Test
    void completeSessionRestoresWhenEnabled() {
        SwapBackTracker tracker = new SwapBackTracker();
        tracker.recordBeforeSwap(3);

        assertEquals(3, tracker.completeSession(true));   // 返回要切回的槽位
        assertFalse(tracker.hasPendingRestore());         // 状态已复位
        assertEquals(SwapBackTracker.NO_SLOT, tracker.previousSlot());
    }

    @Test
    void completeSessionDoesNotRestoreWhenDisabledButStillResets() {
        SwapBackTracker tracker = new SwapBackTracker();
        tracker.recordBeforeSwap(3);

        assertEquals(SwapBackTracker.NO_SLOT, tracker.completeSession(false)); // 开关关闭 → 不切回
        assertFalse(tracker.hasPendingRestore());                              // 但状态复位
        assertEquals(SwapBackTracker.NO_SLOT, tracker.previousSlot());
    }

    /** 开关关闭结束后再开会话：应重新记录当时的槽位（而不是沿用上一会话的）。 */
    @Test
    void recordsFreshSlotOnNextSessionAfterDisabledRun() {
        SwapBackTracker tracker = new SwapBackTracker();
        tracker.recordBeforeSwap(3);
        assertEquals(SwapBackTracker.NO_SLOT, tracker.completeSession(false));

        tracker.recordBeforeSwap(5);
        assertEquals(5, tracker.previousSlot());
        assertEquals(5, tracker.completeSession(true));
    }

    @Test
    void resetClearsState() {
        SwapBackTracker tracker = new SwapBackTracker();
        tracker.recordBeforeSwap(4);
        tracker.reset();

        assertFalse(tracker.hasPendingRestore());
        assertEquals(SwapBackTracker.NO_SLOT, tracker.previousSlot());
        assertEquals(SwapBackTracker.NO_SLOT, tracker.completeSession(true));
    }

    /** 0 号槽是合法槽位，不能被当成「未记录」哨兵吞掉。 */
    @Test
    void slotZeroIsTreatedAsValidRecord() {
        SwapBackTracker tracker = new SwapBackTracker();
        tracker.recordBeforeSwap(0);

        assertEquals(0, tracker.previousSlot());
        assertEquals(0, tracker.completeSession(true));
    }

    /** 会话中途才打开开关：因为记录与开关无关（对齐官方），仍能切回。 */
    @Test
    void enabledMidSessionStillRestores() {
        SwapBackTracker tracker = new SwapBackTracker();
        tracker.recordBeforeSwap(6);                 // 官方在同一分支无条件记录
        assertEquals(6, tracker.completeSession(true));
    }
}
