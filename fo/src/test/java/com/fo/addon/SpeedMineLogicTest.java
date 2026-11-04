package com.fo.addon;

import com.fo.addon.utils.SpeedMineLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FO 发包挖掘纯逻辑测试。
 *
 * <p>锚点来自 Hachimi V2 SpeedMine 实战配置（cheng.json：Speed=0.7）：
 * vanilla 进度累计到 0.7 即发包破坏；一击即碎（delta>=1）直接破坏。</p>
 */
class SpeedMineLogicTest {
    @Test
    void shouldBreak_进度到阈值() {
        // Speed=0.7：进度 0.65 + delta 0.1 = 0.75 >= 0.7 → 破坏
        assertTrue(SpeedMineLogic.shouldBreak(0.65f, 0.1f, 0.7f));
    }

    @Test
    void shouldBreak_进度未到阈值() {
        assertFalse(SpeedMineLogic.shouldBreak(0.5f, 0.1f, 0.7f));
    }

    @Test
    void shouldBreak_一击即碎_直接破坏() {
        assertTrue(SpeedMineLogic.shouldBreak(0.0f, 1.0f, 0.7f));
        assertTrue(SpeedMineLogic.shouldBreak(0.0f, 1.0f, 1.5f));
    }

    @Test
    void shouldBreak_无进度增量_不破坏() {
        assertFalse(SpeedMineLogic.shouldBreak(0.0f, 0.0f, 0.7f));
        assertFalse(SpeedMineLogic.shouldBreak(0.5f, -0.2f, 0.7f));
    }

    @Test
    void accumulate_封顶1() {
        assertEquals(1.0f, SpeedMineLogic.accumulate(0.9f, 0.2f), 1e-6);
        assertEquals(0.3f, SpeedMineLogic.accumulate(0.2f, 0.1f), 1e-6);
        assertEquals(0.2f, SpeedMineLogic.accumulate(0.2f, -0.1f), 1e-6);
    }

    @Test
    void lerpColor_端点和中点() {
        int[] red = {255, 60, 60, 80};
        int[] green = {60, 255, 60, 120};
        int[] s = SpeedMineLogic.lerpColor(red, green, 0);
        assertEquals(255, s[0]);
        int[] e = SpeedMineLogic.lerpColor(red, green, 1);
        assertEquals(255, e[1]);
        int[] m = SpeedMineLogic.lerpColor(red, green, 0.5);
        assertEquals(158, m[0]); // (255+60)/2
        assertEquals(158, m[1]);
    }

    @Test
    void lerpColor_越界裁剪() {
        int[] red = {255, 60, 60, 80};
        int[] green = {60, 255, 60, 120};
        // t>1 裁剪到 t=1 → 终点色；t<0 裁剪到 t=0 → 起点色
        assertEquals(60, SpeedMineLogic.lerpColor(red, green, 2)[0]);
        assertEquals(255, SpeedMineLogic.lerpColor(red, green, -1)[0]);
    }

    // ── Grim 绕过时序（上游 alP() / xt() FX 分支）──

    @Test
    void inSessionCooldown_280ms内_冷却() {
        // 上游常量：上次 STOP 后 280ms 内不开新会话
        assertTrue(SpeedMineLogic.inSessionCooldown(1000, 800, 280));
        assertTrue(SpeedMineLogic.inSessionCooldown(1280, 1000, 280)); // 恰好等于边界：仍算冷却
        assertFalse(SpeedMineLogic.inSessionCooldown(1281, 1000, 280));
        // 很久没发过 STOP（lastStopTime=0）：不冷却
        assertFalse(SpeedMineLogic.inSessionCooldown(5000, 0, 280));
    }

    @Test
    void timeModeStopAllowed_差值小于25ms才放行() {
        // 期望耗时 1000ms，已挖 990ms：差 10 < 25 → 放行
        assertTrue(SpeedMineLogic.timeModeStopAllowed(1000, 990, 25));
        // 已挖 960ms：差 40 >= 25 → 等待
        assertFalse(SpeedMineLogic.timeModeStopAllowed(1000, 960, 25));
        // 超时（挖过头）：放行
        assertTrue(SpeedMineLogic.timeModeStopAllowed(1000, 1100, 25));
    }
}
