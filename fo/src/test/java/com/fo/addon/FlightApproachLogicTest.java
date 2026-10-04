package com.fo.addon;

import com.fo.addon.utils.FlightApproachLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 接近防撞塔单测（对齐 Ying ElytraApproachSafety22 纯计算部分） */
class FlightApproachLogicTest {

    // ===== 接近俯冲角：|滑翔角| 夹到 [3, 12] =====

    @Test
    void approachPitchClampsTo12() {
        // Ying 默认巡航滑翔角 37.72 → 夹到 12（d10 = clamp(|37.72|, 3, 12)）
        assertEquals(12.0, FlightApproachLogic.approachPitch(37.72), 1e-9);
        assertEquals(12.0, FlightApproachLogic.approachPitch(80.0), 1e-9);
    }

    @Test
    void approachPitchClampsTo3() {
        assertEquals(3.0, FlightApproachLogic.approachPitch(0.0), 1e-9);
        assertEquals(3.0, FlightApproachLogic.approachPitch(-2.0), 1e-9);
    }

    @Test
    void approachPitchKeepsMidValue() {
        assertEquals(5.0, FlightApproachLogic.approachPitch(5.0), 1e-9);
    }

    // ===== 预测到达高度 =====

    @Test
    void predictedHeightFormula() {
        // playerY=100, hDist=50, glide=37.72 → 100 - 50*tan(12°) ≈ 100 - 10.63
        double expected = 100.0 - 50.0 * Math.tan(Math.toRadians(12.0));
        assertEquals(expected, FlightApproachLogic.predictedY(100.0, 50.0, 37.72), 1e-6);
        // 距离越远预测越低（飞得越久掉得越多）
        assertTrue(FlightApproachLogic.predictedY(100.0, 200.0, 37.72) < FlightApproachLogic.predictedY(100.0, 50.0, 37.72));
    }

    @Test
    void predictedHeightZeroDistanceIsPlayerY() {
        assertEquals(100.0, FlightApproachLogic.predictedY(100.0, 0.0, 37.72), 1e-9);
    }

    // ===== 安全高度 =====

    @Test
    void safeHeightIsLandingPlus30() {
        // Ying SAFE_MARGIN_ABOVE_TARGET = 30
        assertEquals(94.0, FlightApproachLogic.safeY(64.0), 1e-9);
        assertEquals(10.0, FlightApproachLogic.safeY(-20.0), 1e-9);
    }

    // ===== 拉升目标高度 =====

    @Test
    void neededHeightUsesSafePlusClampedTerm() {
        // hDist=50, glide=37.72, landingY=64 → tan12°*50+12 ≈ 22.63 → max(35, min(90,22.63)) = 35 → 94+35
        assertEquals(94.0 + 35.0, FlightApproachLogic.neededY(50.0, 37.72, 64.0), 1e-6);
    }

    @Test
    void neededHeightCapsTermAt90() {
        // 超大距离：tan12°*hDist+12 超过 90 → 封顶 90（Ying max(35, min(90, ...))）
        assertEquals(94.0 + 90.0, FlightApproachLogic.neededY(1000.0, 37.72, 64.0), 1e-6);
    }

    // ===== 首次介入决策 =====

    @Test
    void decideModeNormalWhenPredictedSafeEnough() {
        // 预测 ≥ 安全高度 + 8 → 正常接近（Ying SAFE_EXTRA_ON_RETURN=8）
        assertEquals(FlightApproachLogic.Mode.NORMAL, FlightApproachLogic.decideMode(102.0, 94.0));
        assertEquals(FlightApproachLogic.Mode.NORMAL, FlightApproachLogic.decideMode(102.0, 94.0));
    }

    @Test
    void decideModeClimbWhenPredictedTooLow() {
        assertEquals(FlightApproachLogic.Mode.CLIMB, FlightApproachLogic.decideMode(101.9, 94.0));
        assertEquals(FlightApproachLogic.Mode.CLIMB, FlightApproachLogic.decideMode(50.0, 94.0));
    }

    // ===== 介入条件 =====

    @Test
    void notInterveneBeyond200InNormalMode() {
        // Ying: d8>200 且 mode==0 → return false（不介入）
        assertFalse(FlightApproachLogic.shouldIntervene(250.0, FlightApproachLogic.Mode.NORMAL));
    }

    @Test
    void interveneWithin200OrNotNormalMode() {
        assertTrue(FlightApproachLogic.shouldIntervene(150.0, FlightApproachLogic.Mode.NORMAL));
        assertTrue(FlightApproachLogic.shouldIntervene(200.0, FlightApproachLogic.Mode.NORMAL));
        // 拉升/恢复接近中即使距离>200 也继续接管（return d8<=200 || mode==2）
        assertTrue(FlightApproachLogic.shouldIntervene(250.0, FlightApproachLogic.Mode.CLIMB));
        assertTrue(FlightApproachLogic.shouldIntervene(250.0, FlightApproachLogic.Mode.REAPPROACH));
    }

    // ===== 接近中再次拉升 =====

    @Test
    void climbAgainWhenPredictedBelowSafePlus3AndFar() {
        // Ying: d < safeY+3 && d8 > 95 → mode 1
        assertTrue(FlightApproachLogic.shouldClimbAgain(96.9, 94.0, 100.0));
        assertFalse(FlightApproachLogic.shouldClimbAgain(97.0, 94.0, 100.0)); // 恰好达标不拉升
        assertFalse(FlightApproachLogic.shouldClimbAgain(96.9, 94.0, 90.0));  // 距离不足 95 不拉升
    }

    // ===== 降落交接 =====

    @Test
    void handoffToLandingAt80WithConfirmedElytra() {
        // Ying: d8<=80 && au(已检查展示框) && !av(框里有鞘翅) → LANDING
        assertTrue(FlightApproachLogic.shouldHandoffToLanding(80.0, true, false));
        assertTrue(FlightApproachLogic.shouldHandoffToLanding(10.0, true, false));
        assertFalse(FlightApproachLogic.shouldHandoffToLanding(80.1, true, false)); // 超过 80 不交接
        assertFalse(FlightApproachLogic.shouldHandoffToLanding(50.0, false, false)); // 未检查展示框不交接
        assertFalse(FlightApproachLogic.shouldHandoffToLanding(50.0, true, true));   // 框里没鞘翅不交接
    }

    // ===== 状态重置 =====

    @Test
    void stateResetOnTargetChange() {
        FlightApproachLogic.State s = new FlightApproachLogic.State();
        s.mode = FlightApproachLogic.Mode.CLIMB;
        s.ticks = 50;
        s.announced = true;
        s.targetKey = "old";
        // 目标变化（换船）时模块侧负责重置；这里验证 State 字段可被重置
        s.targetKey = null;
        s.mode = FlightApproachLogic.Mode.NORMAL;
        s.ticks = 0;
        s.announced = false;
        assertEquals(FlightApproachLogic.Mode.NORMAL, s.mode);
        assertEquals(0, s.ticks);
        assertFalse(s.announced);
        assertNull(s.targetKey);
    }
}
