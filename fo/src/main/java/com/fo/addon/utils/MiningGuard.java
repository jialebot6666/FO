package com.fo.addon.utils;

/**
 * 自动挖矿内部判定逻辑（纯函数，可单测，不依赖 MC 运行时）。
 */
public final class MiningGuard {

    private MiningGuard() {}

    /**
     * 是否应跳过"强制锁时运镐"：AutoEat 正在吃、玩家正在使用物品、
     * 或 FO杀戮光环正在攻击（已切到武器）时放行，避免互相抢槽位导致死循环卡死。
     */
    public static boolean shouldSkipFortuneLock(boolean usingItem, boolean autoEatEating, boolean killAuraAttacking) {
        return usingItem || autoEatEating || killAuraAttacking;
    }
}
