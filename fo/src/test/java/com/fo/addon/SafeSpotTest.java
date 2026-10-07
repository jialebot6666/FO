package com.fo.addon;

import com.fo.addon.modules.AutoMining;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 安全位置候选判定测试（V4.51 对齐 misaka x0005）：
 *  ① Y 下限：候选 y-2 ≥ minY（主世界 -58 / 地狱 6）
 *  ② 5x5x5 全安全实体（x0015 语义） */
public class SafeSpotTest {

    // ---------- Y 下限边界 ----------

    @Test
    public void overworldMinY_allowsDeepSlateAndShallow() {
        // 主世界 minY = -58：深板岩层（y=-56 → y-2=-58）与浅层（y=10 → y-2=8）都允许
        assertTrue(AutoMining.isSafeSpotCandidate(-58, -58, true));
        assertTrue(AutoMining.isSafeSpotCandidate(8, -58, true));
        assertTrue(AutoMining.isSafeSpotCandidate(0, -58, true));
    }

    @Test
    public void overworldMinY_rejectsBelowLimit() {
        // y=-57 → y-2=-59 < -58 → 拒绝
        assertFalse(AutoMining.isSafeSpotCandidate(-59, -58, true));
    }

    @Test
    public void netherMinY_requiresAboveBedrock() {
        // 地狱 minY = 6：候选 y=8 → y-2=6 恰好通过；y=7 → y-2=5 < 6 拒绝（避开基岩 y 0-4）
        assertTrue(AutoMining.isSafeSpotCandidate(6, 6, true));
        assertFalse(AutoMining.isSafeSpotCandidate(5, 6, true));
        assertFalse(AutoMining.isSafeSpotCandidate(2, 6, true));
    }

    // ---------- 5x5x5 检查 ----------

    @Test
    public void solid5x5x5Required() {
        // 5x5x5 区域不合格（含空气/岩浆等）→ 拒绝，即使 Y 达标
        assertFalse(AutoMining.isSafeSpotCandidate(8, -58, false));
        assertFalse(AutoMining.isSafeSpotCandidate(6, 6, false));
    }

    @Test
    public void bothConditionsNeeded() {
        // 双条件缺一不可
        assertFalse(AutoMining.isSafeSpotCandidate(-59, -58, false));
        assertTrue(AutoMining.isSafeSpotCandidate(-58, -58, true));
    }
}
