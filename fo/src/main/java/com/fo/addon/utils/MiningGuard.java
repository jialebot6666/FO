package com.fo.addon.utils;

/**
 * 自动挖矿内部判定逻辑（纯函数，可单测，不依赖 MC 运行时）。
 */
public final class MiningGuard {

    private MiningGuard() {}

    /**
     * 是否应跳过"强制锁时运镐"：AutoEat 正在吃或玩家正在使用物品时放行，
     * 避免与 Meteor 原版自动吃互相抢槽位导致死循环卡死。
     */
    public static boolean shouldSkipFortuneLock(boolean usingItem, boolean autoEatEating) {
        return usingItem || autoEatEating;
    }
}
