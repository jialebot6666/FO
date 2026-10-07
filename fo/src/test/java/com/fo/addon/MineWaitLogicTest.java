package com.fo.addon;

import com.fo.addon.elytra.core.MineWaitLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「交给 Baritone 挖方块 → 等方块进背包」的等待判定（{@link MineWaitLogic}）。
 *
 * <h2>这个测试锁的是 V5.1 修掉的那个误报</h2>
 * 旧代码（继承自 IceHack）在把挖掘交给 Baritone 之后只等 40 tick（2 秒），
 * 只要 {@code isMining()} 还是 true 就报「挖掘异常？取消挖掘」并 stop 掉进程。
 * 但 Baritone 的 mine 是<b>进程</b>：寻路 → 挖掉 → 走过去捡掉落物，全程 isActive 都是 true，
 * 2 秒连"走过去 + 挖掉"都不够 —— 所以每次都误报。
 *
 * <p>下面 {@link #blockStillThereIsNeverAnAnomaly()} 就是钉住这条：<b>方块还在的时候绝不能停挖</b>。</p>
 */
class MineWaitLogicTest {

    // ------------------------------------------------------------------ evaluate

    @Test
    void waitingWhileBlockStillThere() {
        assertEquals(MineWaitLogic.Outcome.WAITING,
            MineWaitLogic.evaluate(false, false, 30, MineWaitLogic.SHULKER_TIMEOUT_TICKS));
    }

    @Test
    void collectedWhenBlockGoneAndItemInInventory() {
        assertEquals(MineWaitLogic.Outcome.COLLECTED,
            MineWaitLogic.evaluate(true, true, 10, MineWaitLogic.SHULKER_TIMEOUT_TICKS));
    }

    @Test
    void blockGoneButItemNotPickedUpYetKeepsWaiting() {
        // 方块已经没了、掉落物还在飞 —— 这段时间应该继续等，不是失败也不是异常
        assertEquals(MineWaitLogic.Outcome.WAITING,
            MineWaitLogic.evaluate(true, false, 10, MineWaitLogic.SHULKER_TIMEOUT_TICKS));
    }

    @Test
    void timesOutAfterTimeoutTicks() {
        assertEquals(MineWaitLogic.Outcome.TIMED_OUT,
            MineWaitLogic.evaluate(false, false, MineWaitLogic.SHULKER_TIMEOUT_TICKS + 1,
                MineWaitLogic.SHULKER_TIMEOUT_TICKS));
    }

    @Test
    void exactlyAtTimeoutStillWaits() {
        // 边界：只有严格大于才算超时（与原实现的 > TIMEOUT 一致）
        assertEquals(MineWaitLogic.Outcome.WAITING,
            MineWaitLogic.evaluate(false, false, MineWaitLogic.SHULKER_TIMEOUT_TICKS,
                MineWaitLogic.SHULKER_TIMEOUT_TICKS));
    }

    @Test
    void successBeatsTimeoutOnTheSameTick() {
        // 恰好在超时那一 tick 把东西捡起来，必须判成功而不是失败
        assertEquals(MineWaitLogic.Outcome.COLLECTED,
            MineWaitLogic.evaluate(true, true, MineWaitLogic.SHULKER_TIMEOUT_TICKS + 5,
                MineWaitLogic.SHULKER_TIMEOUT_TICKS));
    }

    // ------------------------------------------------------------------ shouldStopMining

    @Test
    void stopsMiningOnlyAfterPickupWindowWhenBlockGone() {
        assertFalse(MineWaitLogic.shouldStopMining(true, MineWaitLogic.PICKUP_WINDOW_TICKS - 1,
            MineWaitLogic.PICKUP_WINDOW_TICKS, true), "窗口未到不该打断");
        assertTrue(MineWaitLogic.shouldStopMining(true, MineWaitLogic.PICKUP_WINDOW_TICKS,
            MineWaitLogic.PICKUP_WINDOW_TICKS, true), "窗口到了应该收尾");
        assertTrue(MineWaitLogic.shouldStopMining(true, MineWaitLogic.PICKUP_WINDOW_TICKS + 20,
            MineWaitLogic.PICKUP_WINDOW_TICKS, true), "窗口过了也应该收尾");
    }

    @Test
    void blockStillThereIsNeverAnAnomaly() {
        // ★ 本次修复的核心：方块还在 = Baritone 正常在挖，无论等了多少 tick 都不能停挖
        for (int waited : new int[] { 1, 39, 40, 41, 120, 121, 200, 5000 }) {
            assertFalse(MineWaitLogic.shouldStopMining(false, waited, MineWaitLogic.PICKUP_WINDOW_TICKS, true),
                "方块还在时不该停挖（waited=" + waited + "）");
        }
        // 旧代码正是在第 40 tick 拿「还在挖」当异常判据的 —— 现在第 40 tick 必须是「继续等」
        assertEquals(MineWaitLogic.Outcome.WAITING,
            MineWaitLogic.evaluate(false, false, 40, MineWaitLogic.SHULKER_TIMEOUT_TICKS),
            "第 40 tick 方块还在 = Baritone 正常在挖，必须继续等而不是报异常");
    }

    @Test
    void doesNotStopMiningWhenBaritoneAlreadyIdle() {
        assertFalse(MineWaitLogic.shouldStopMining(true, 100, MineWaitLogic.PICKUP_WINDOW_TICKS, false),
            "Baritone 已经不在挖了就不需要再停一次");
    }

    // ------------------------------------------------------------------ isStuck

    @Test
    void stuckOnlyWhenBlockRemainsPastTimeout() {
        assertFalse(MineWaitLogic.isStuck(false, MineWaitLogic.SHULKER_TIMEOUT_TICKS,
            MineWaitLogic.SHULKER_TIMEOUT_TICKS), "刚好到阈值不算卡住");
        assertTrue(MineWaitLogic.isStuck(false, MineWaitLogic.SHULKER_TIMEOUT_TICKS + 1,
            MineWaitLogic.SHULKER_TIMEOUT_TICKS), "超过阈值且方块还在才算卡住");
        assertFalse(MineWaitLogic.isStuck(true, MineWaitLogic.SHULKER_TIMEOUT_TICKS + 100,
            MineWaitLogic.SHULKER_TIMEOUT_TICKS), "方块都没了就不叫卡住");
    }

    // ------------------------------------------------------------------ 常量

    @Test
    void timeoutConstantsAreSane() {
        assertTrue(MineWaitLogic.PICKUP_WINDOW_TICKS > 0);
        assertTrue(MineWaitLogic.SHULKER_TIMEOUT_TICKS > MineWaitLogic.PICKUP_WINDOW_TICKS,
            "整体超时必须大于拾取窗口，否则方块还在就先超时了");
        assertTrue(MineWaitLogic.ENDER_CHEST_TIMEOUT_TICKS > MineWaitLogic.SHULKER_TIMEOUT_TICKS,
            "末影箱要挖黑曜石，超时必须比潜影盒长");
    }
}
