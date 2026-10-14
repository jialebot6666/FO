package com.fo.addon;

import com.fo.addon.utils.CruisePitchLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 巡航俯仰曲线「快拉慢压」单测（对齐 Meteor Pitch40） */
class CruisePitchLogicTest {

    private static final double CLIMB = 54.77;
    private static final double GLIDE = 37.72;
    private static final double UP = 5.45;
    private static final double DOWN = 0.90;

    // ===== 爬升段：向 -爬升角 抬头 =====

    @Test
    void climbStepsUpTowardNegativeClimbAngle() {
        // 从滑翔角开始爬升：每 tick 抬头 upSpeed
        assertEquals(32.27, CruisePitchLogic.step(37.72f, true, CLIMB, GLIDE, UP, DOWN), 0.001);
        assertEquals(-50.0, CruisePitchLogic.step(-44.55f, true, CLIMB, GLIDE, UP, DOWN), 0.001);
    }

    @Test
    void climbClampsAtTarget() {
        // 一步会越界 → 直接停在目标角，不会冲过头
        assertEquals(-54.77, CruisePitchLogic.step(-50.0f, true, CLIMB, GLIDE, UP, DOWN), 0.001);
        // 已在目标角 → 不再变化
        assertEquals(-54.77, CruisePitchLogic.step(-54.77f, true, CLIMB, GLIDE, UP, DOWN), 0.001);
    }

    // ===== 滑翔段：向 +滑翔角 缓慢下压 =====

    @Test
    void glideStepsDownTowardPositiveGlideAngle() {
        assertEquals(-53.87, CruisePitchLogic.step(-54.77f, false, CLIMB, GLIDE, UP, DOWN), 0.001);
        assertEquals(10.90, CruisePitchLogic.step(10.0f, false, CLIMB, GLIDE, UP, DOWN), 0.001);
    }

    @Test
    void glideClampsAtTarget() {
        assertEquals(GLIDE, CruisePitchLogic.step(37.0f, false, CLIMB, GLIDE, UP, DOWN), 0.001);
        assertEquals(GLIDE, CruisePitchLogic.step(37.72f, false, CLIMB, GLIDE, UP, DOWN), 0.001);
    }

    // ===== 「快拉慢压」的核心特征：同样角度跨度，拉上去远快于压回来 =====

    @Test
    void pullUpIsMuchFasterThanEaseDown() {
        float pitch = (float) GLIDE;
        int climbTicks = 0;
        while (pitch > -CLIMB && climbTicks < 1000) {
            pitch = CruisePitchLogic.step(pitch, true, CLIMB, GLIDE, UP, DOWN);
            climbTicks++;
        }
        assertEquals(-CLIMB, pitch, 0.001);

        int glideTicks = 0;
        while (pitch < GLIDE && glideTicks < 1000) {
            pitch = CruisePitchLogic.step(pitch, false, CLIMB, GLIDE, UP, DOWN);
            glideTicks++;
        }
        assertEquals(GLIDE, pitch, 0.001);

        // 92.49 度 ÷ 5.45 ≈ 17 tick；÷ 0.90 ≈ 103 tick
        assertEquals(17, climbTicks);
        assertEquals(103, glideTicks);
        assertTrue(climbTicks * 5 < glideTicks, "拉起应远快于下压");
    }

    // ===== 换段立即生效：滑翔中途翻成爬升，下一 tick 就开始抬头 =====

    @Test
    void switchingToClimbTakesEffectImmediately() {
        float mid = -20f;
        float next = CruisePitchLogic.step(mid, true, CLIMB, GLIDE, UP, DOWN);
        assertTrue(next < mid, "翻成爬升段后俯仰角应立刻变小（更抬头）");
    }

    // ===== 速度为 0 / 参数退化 =====

    @Test
    void zeroSpeedKeepsPitchUnchanged() {
        assertEquals(-30f, CruisePitchLogic.step(-30f, true, CLIMB, GLIDE, 0, 0), 0.001);
        assertEquals(10f, CruisePitchLogic.step(10f, false, CLIMB, GLIDE, 0, 0), 0.001);
    }
}
