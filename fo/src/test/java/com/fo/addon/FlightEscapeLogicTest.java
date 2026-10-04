package com.fo.addon;

import com.fo.addon.utils.FlightEscapeLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 安全复飞单测（对齐 Ying ElytraSafeEscape 三段式纯计算部分） */
class FlightEscapeLogicTest {

    // ===== 角度归一化 =====

    @Test
    void normalizeYawToNeg180Pos180() {
        assertEquals(-180f, FlightEscapeLogic.normalizeYaw(180f), 1e-6); // 归一化到 [-180,180)，180 → -180（角度等价）
        assertEquals(-170f, FlightEscapeLogic.normalizeYaw(190f), 1e-6);
        assertEquals(170f, FlightEscapeLogic.normalizeYaw(-190f), 1e-6);
        assertEquals(45f, FlightEscapeLogic.normalizeYaw(45f), 1e-6);
        assertEquals(-90f, FlightEscapeLogic.normalizeYaw(270f), 1e-6);
    }

    // ===== 背离方向 =====

    @Test
    void escapeYawIsOppositeOfTarget() {
        // 背对降落点：yawTo + 180
        assertEquals(-180f, FlightEscapeLogic.escapeYaw(0f), 1e-6); // 180 → -180（角度等价）
        assertEquals(-90f, FlightEscapeLogic.escapeYaw(90f), 1e-6); // 270 → -90
        assertEquals(0f, FlightEscapeLogic.escapeYaw(180f), 1e-6);
    }

    // ===== 阶段0→1：背离 90 格 =====

    @Test
    void startClimbAfter90Blocks() {
        // Ying ESCAPE_DISTANCE = 90
        assertTrue(FlightEscapeLogic.shouldStartClimb(90.0));
        assertTrue(FlightEscapeLogic.shouldStartClimb(150.0));
        assertFalse(FlightEscapeLogic.shouldStartClimb(89.9));
    }

    // ===== 阶段1→2：爬升到降落点上方 30 格 =====

    @Test
    void startReapproachAtLandingPlus30() {
        // Ying SAFE_HEIGHT_ABOVE_LANDING = 30
        assertTrue(FlightEscapeLogic.shouldStartReapproach(94.0, 64.0));
        assertTrue(FlightEscapeLogic.shouldStartReapproach(100.0, 64.0));
        assertFalse(FlightEscapeLogic.shouldStartReapproach(93.9, 64.0));
    }

    // ===== 阶段2 高度不足 → 回阶段0 =====

    @Test
    void retreatWhenLowAndBeyond35() {
        // Ying: Y < landing+10 && d9 > 35 → 回背离
        assertTrue(FlightEscapeLogic.shouldRetreat(73.9, 64.0, 40.0));
        assertFalse(FlightEscapeLogic.shouldRetreat(74.0, 64.0, 40.0)); // 恰好达标不撤离
        assertFalse(FlightEscapeLogic.shouldRetreat(73.9, 64.0, 35.0));  // 距离不足 35 不撤离
        assertFalse(FlightEscapeLogic.shouldRetreat(100.0, 64.0, 100.0)); // 高度足够不撤离
    }

    // ===== 阶段2 完成 → 转降落 =====

    @Test
    void handoffToLandingWithin28AndEnoughHeight() {
        // Ying: d9 <= 28 && Y >= landing+10 → LANDING
        assertTrue(FlightEscapeLogic.shouldHandoffToLanding(74.0, 64.0, 28.0));
        assertTrue(FlightEscapeLogic.shouldHandoffToLanding(74.0, 64.0, 10.0));
        assertFalse(FlightEscapeLogic.shouldHandoffToLanding(74.0, 64.0, 28.1)); // 超 28 不交接
        assertFalse(FlightEscapeLogic.shouldHandoffToLanding(73.9, 64.0, 28.0)); // 高度不足不交接
    }

    // ===== 阶段0 烟花节奏 =====

    @Test
    void escapeFireworkAfter60TicksEvery40() {
        // Ying: n2 >= 60 && n2 % max(interval, 40) == 0 → 60, 100, 140 不放；80, 120, 160 放
        assertFalse(FlightEscapeLogic.escapeFireworkDue(60, 40)); // 60 % 40 = 20
        assertTrue(FlightEscapeLogic.escapeFireworkDue(80, 40));  // 80 % 40 = 0
        assertTrue(FlightEscapeLogic.escapeFireworkDue(120, 40));
        assertFalse(FlightEscapeLogic.escapeFireworkDue(59, 40)); // 未到 60 tick
        assertFalse(FlightEscapeLogic.escapeFireworkDue(61, 40)); // 61 % 40 = 21
        assertFalse(FlightEscapeLogic.escapeFireworkDue(100, 40)); // 100 % 40 = 20
    }

    // ===== 阶段1 烟花节奏 =====

    @Test
    void climbFireworkFirstTickAndEveryInterval() {
        // Ying: n2 == 1 || n2 % interval == 0
        assertTrue(FlightEscapeLogic.climbFireworkDue(1, 40));
        assertTrue(FlightEscapeLogic.climbFireworkDue(40, 40));
        assertTrue(FlightEscapeLogic.climbFireworkDue(80, 40));
        assertFalse(FlightEscapeLogic.climbFireworkDue(2, 40));
        assertFalse(FlightEscapeLogic.climbFireworkDue(39, 40));
    }

    // ===== 烟花间隔换算 =====

    @Test
    void fireworkIntervalTicksFromSeconds() {
        assertEquals(40, FlightEscapeLogic.fireworkIntervalTicks(2.0));
        assertEquals(10, FlightEscapeLogic.fireworkIntervalTicks(0.5));
        assertEquals(20, FlightEscapeLogic.fireworkIntervalTicks(1.0));
        assertEquals(1, FlightEscapeLogic.fireworkIntervalTicks(0.01)); // 至少 1 tick
    }
}
