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
}
