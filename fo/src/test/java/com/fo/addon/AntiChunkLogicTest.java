package com.fo.addon;

import com.fo.addon.utils.AntiChunkLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 防卡区块爬升判定单测（Ying 0.7.2 Pitch40Fly 内嵌版） */
class AntiChunkLogicTest {

    // ===== 触发拉升 =====

    @Test
    void triggersWhenUnloadedChunkInsideLookahead() {
        assertTrue(AntiChunkLogic.shouldTrigger(0, 32));
        assertTrue(AntiChunkLogic.shouldTrigger(16, 32));
        assertTrue(AntiChunkLogic.shouldTrigger(32, 32));   // 边界：等于阈值也触发
    }

    @Test
    void doesNotTriggerWhenAheadBeyondLookaheadOrFullyLoaded() {
        assertFalse(AntiChunkLogic.shouldTrigger(33, 32));
        assertFalse(AntiChunkLogic.shouldTrigger(-1, 32));  // -1 = 前方全部已加载
    }

    // ===== 安全性判定（结束拉升条件）=====

    @Test
    void clearWhenAheadBeyondLookaheadPlusMargin() {
        assertTrue(AntiChunkLogic.isClear(49, 32, 16));   // 32+16=48，49 算安全
        assertFalse(AntiChunkLogic.isClear(48, 32, 16));  // 等于 48 仍不够
        assertTrue(AntiChunkLogic.isClear(-1, 32, 16));   // 全加载 = 安全
    }

    @Test
    void cannotEndClimbBeforeMinTicksOrStreak() {
        // 最短 10 tick、连续 3 tick 安全，两者缺一不可
        assertFalse(AntiChunkLogic.canEndClimb(9, 3, 10));
        assertFalse(AntiChunkLogic.canEndClimb(10, 2, 10));
        assertTrue(AntiChunkLogic.canEndClimb(10, 3, 10));
        assertTrue(AntiChunkLogic.canEndClimb(11, 5, 10));
        // minTicks=0 时只需连续 3 tick
        assertFalse(AntiChunkLogic.canEndClimb(0, 2, 0));
        assertTrue(AntiChunkLogic.canEndClimb(0, 3, 0));
    }

    // ===== 俯仰平滑 =====

    @Test
    void stepTowardMovesBySpeedAndClamps() {
        assertEquals(32.27f, AntiChunkLogic.stepToward(37.72f, -54.77f, 5.45f), 0.0001f);
        assertEquals(38.62f, AntiChunkLogic.stepToward(37.72f, 50f, 0.9f), 0.0001f);
        // 距离小于步长时直接到目标
        assertEquals(5f, AntiChunkLogic.stepToward(10f, 5f, 6f), 0.0001f);
        assertEquals(-54.77f, AntiChunkLogic.stepToward(-50f, -54.77f, 5.45f), 0.0001f);
    }
}
