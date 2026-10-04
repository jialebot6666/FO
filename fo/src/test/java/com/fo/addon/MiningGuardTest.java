package com.fo.addon;

import com.fo.addon.utils.MiningGuard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MiningGuard 纯逻辑测试：AutoEat 保护判定 */
public class MiningGuardTest {

    @Test
    void skipWhenUsingItemOrEating() {
        // 正在使用物品（吃/喝）→ 跳过锁工具
        assertTrue(MiningGuard.shouldSkipFortuneLock(true, false));
        // AutoEat 正在吃 → 跳过锁工具
        assertTrue(MiningGuard.shouldSkipFortuneLock(false, true));
        // 两者都满足 → 跳过
        assertTrue(MiningGuard.shouldSkipFortuneLock(true, true));
    }

    @Test
    void lockWhenIdle() {
        // 既不在使用物品也没有在吃 → 正常锁时运镐
        assertFalse(MiningGuard.shouldSkipFortuneLock(false, false));
    }
}
