package com.fo.addon;

import com.fo.addon.utils.FlightFireworkLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 巡航烟花使用判定单测（对齐 Ying 爬升烧/下滑不烧） */
class FlightFireworkLogicTest {

    // ===== 下滑（滑翔）阶段：不烧烟花 =====

    @Test
    void glideNeverUsesFirework() {
        // 下滑时即使间隔已到也不烧（省烟花，对齐 Ying "滑翔阶段不使用烟花"）
        assertFalse(FlightFireworkLogic.shouldUseFirework(false, false, 40, 0, 20));
        assertFalse(FlightFireworkLogic.shouldUseFirework(false, false, 200, 0, 20));
        assertFalse(FlightFireworkLogic.shouldUseFirework(false, false, 5, 0, 1));
    }

    // ===== 爬升阶段：按间隔烧 =====

    @Test
    void climbUsesFireworkWhenIntervalElapsed() {
        assertTrue(FlightFireworkLogic.shouldUseFirework(true, false, 40, 20, 20));
        assertTrue(FlightFireworkLogic.shouldUseFirework(true, false, 100, 0, 20));
    }

    @Test
    void climbSkipsFireworkWhenIntervalNotElapsed() {
        assertFalse(FlightFireworkLogic.shouldUseFirework(true, false, 5, 0, 20));
        assertFalse(FlightFireworkLogic.shouldUseFirework(true, false, 19, 0, 20));
        assertFalse(FlightFireworkLogic.shouldUseFirework(true, false, 20, 20, 20));
    }

    @Test
    void intervalTicksMinimumIs1() {
        // 间隔为 0 时按 1 tick 兜底（Math.max(1, interval)），避免每 tick 判定恒真
        assertTrue(FlightFireworkLogic.shouldUseFirework(true, false, 1, 0, 0));
        assertFalse(FlightFireworkLogic.shouldUseFirework(true, false, 0, 0, 0));
    }

    // ===== 紧急拉升：无条件按间隔烧 =====

    @Test
    void emergencyClimbUsesFireworkEvenWhenGliding() {
        // frameAirEmpty 紧急拉升（含下滑中途拉升）：必须烧，防止速度掉光坠落
        assertTrue(FlightFireworkLogic.shouldUseFirework(false, true, 40, 0, 20));
        assertTrue(FlightFireworkLogic.shouldUseFirework(true, true, 40, 0, 20));
    }

    @Test
    void emergencyClimbStillRespectsInterval() {
        assertFalse(FlightFireworkLogic.shouldUseFirework(false, true, 10, 0, 20));
        assertFalse(FlightFireworkLogic.shouldUseFirework(false, true, 20, 20, 20));
    }

    // ===== 巡航「按需补烟花」（对齐 Pitch40Util auto-firework） =====

    private static final double THRESHOLD = -0.05;
    private static final double UPPER = 220.0;

    @Test
    void onDemandTopsUpWhenFallingWithVelocity() {
        // 爬升段、速度已掉到阈值以下、未到上界、冷却已过 → 补一发
        assertTrue(FlightFireworkLogic.shouldTopUpOnDemand(true, true, -0.10, THRESHOLD, 200.0, UPPER, 0));
        assertTrue(FlightFireworkLogic.shouldTopUpOnDemand(true, true, -0.90, THRESHOLD, 150.0, UPPER, 0));
    }

    @Test
    void onDemandKeepsQuietWhileStillClimbing() {
        // 还在正常爬升（速度为正）→ 一个都不烧，这正是「省烟花」的关键
        assertFalse(FlightFireworkLogic.shouldTopUpOnDemand(true, true, 0.40, THRESHOLD, 200.0, UPPER, 0));
        assertFalse(FlightFireworkLogic.shouldTopUpOnDemand(true, true, 0.00, THRESHOLD, 200.0, UPPER, 0));
        assertFalse(FlightFireworkLogic.shouldTopUpOnDemand(true, true, -0.05, THRESHOLD, 200.0, UPPER, 0));
    }

    @Test
    void onDemandNeverFiresWhileGliding() {
        // 滑翔段速度恒为负，若不限制会被判定成「一直在掉速」而烧光烟花
        assertFalse(FlightFireworkLogic.shouldTopUpOnDemand(true, false, -1.20, THRESHOLD, 200.0, UPPER, 0));
        assertFalse(FlightFireworkLogic.shouldTopUpOnDemand(true, false, -0.30, THRESHOLD, 150.0, UPPER, 0));
    }

    @Test
    void onDemandRespectsCooldown() {
        assertFalse(FlightFireworkLogic.shouldTopUpOnDemand(true, true, -0.5, THRESHOLD, 200.0, UPPER, 1));
        assertFalse(FlightFireworkLogic.shouldTopUpOnDemand(true, true, -0.5, THRESHOLD, 200.0, UPPER, 10));
        assertTrue(FlightFireworkLogic.shouldTopUpOnDemand(true, true, -0.5, THRESHOLD, 200.0, UPPER, 0));
    }

    @Test
    void onDemandStopsAtUpperBound() {
        // 已到/超过上界：该转滑翔了，不再补烟花
        assertFalse(FlightFireworkLogic.shouldTopUpOnDemand(true, true, -0.5, THRESHOLD, UPPER, UPPER, 0));
        assertFalse(FlightFireworkLogic.shouldTopUpOnDemand(true, true, -0.5, THRESHOLD, UPPER + 10, UPPER, 0));
        assertTrue(FlightFireworkLogic.shouldTopUpOnDemand(true, true, -0.5, THRESHOLD, UPPER - 1, UPPER, 0));
    }

    @Test
    void onDemandHonoursTheToggle() {
        // 关掉「巡航按需补烟花」→ 再满足条件也不烧
        assertFalse(FlightFireworkLogic.shouldTopUpOnDemand(false, true, -0.9, THRESHOLD, 150.0, UPPER, 0));
    }
}
