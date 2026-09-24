package com.fo.addon.utils;

/**
 * 核爆模式没沙时的下一步动作决策（纯逻辑，可单测）。
 *
 * 原则：核爆全程开启、不停下等待捡掉落物，但掉落物要收集（走路自动拾取不够，需要 Baritone pickup）：
 * - 正在寻路/拾取中 → 等（不重复指路）；
 * - 有沙掉落物 → 用 Baritone pickup 走去捡（FollowProcess 自动走完、捡完即止，不会卡死等待）；
 * - 没掉落物但大范围还有沙 → 走去最近沙块；
 * - 都没有 → 等。
 */
public final class NukerMoveLogic {

    public enum Action {
        PICKUP,        // 走去捡沙掉落物（Baritone pickup）
        MOVE_TO_SAND,  // 走去最近沙块
        WAIT           // 等待（正在寻路 / 无目标）
    }

    private NukerMoveLogic() {
    }

    public static Action decide(boolean hasSandDrops, boolean hasAnySandNear, boolean pathing) {
        if (pathing) return Action.WAIT;
        if (hasSandDrops) return Action.PICKUP;
        if (hasAnySandNear) return Action.MOVE_TO_SAND;
        return Action.WAIT;
    }
}
