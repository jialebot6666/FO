package com.fo.addon;

import com.fo.addon.utils.MiningGuard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MiningGuard 纯逻辑测试：AutoEat/杀戮光环保护判定 */
public class MiningGuardTest {

    @Test
    void skipWhenUsingItemOrEatingOrKillAura() {
        // 正在使用物品（吃/喝）→ 跳过锁工具
        assertTrue(MiningGuard.shouldSkipFortuneLock(true, false, false));
        // AutoEat 正在吃 → 跳过锁工具
        assertTrue(MiningGuard.shouldSkipFortuneLock(false, true, false));
        // FO杀戮光环正在攻击 → 跳过锁工具
        assertTrue(MiningGuard.shouldSkipFortuneLock(false, false, true));
        // 三者任一满足 → 跳过
        assertTrue(MiningGuard.shouldSkipFortuneLock(true, true, true));
    }

    @Test
    void lockWhenAllIdle() {
        // 未使用物品、未在吃、杀戮光环未攻击 → 正常锁时运镐
        assertFalse(MiningGuard.shouldSkipFortuneLock(false, false, false));
    }
}
