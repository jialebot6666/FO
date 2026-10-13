package com.fo.addon.utils;

/**
 * AutoTree 纯决策逻辑（不依赖 Minecraft 运行时，可单元测试）。
 *
 * <p>决策表移植自 LeavesHack (CC0) AutoTree 的循环体：
 * 目标方块上方是已种树苗 → 用骨粉催熟；上方是空气/可替换 → 种植树苗。
 */
public final class TreeLogic {

    public enum Action {
        /** 对已种树苗使用骨粉 */
        BONEMEAL,
        /** 在空位放置树苗 */
        PLANT,
        /** 本 tick 不操作 */
        NONE
    }

    private TreeLogic() {}

    /**
     * @param upIsSapling      目标方块正上方是否为已种下的树苗
     * @param upIsAirOrReplaceable 目标方块正上方是否为空/可替换（可种植）
     * @param useBoneMeal      是否开启骨粉功能
     * @param hasBoneMeal      背包中是否有骨粉
     * @param hasSapling       背包中是否有树苗
     */
    public static Action decide(boolean upIsSapling, boolean upIsAirOrReplaceable,
                                boolean useBoneMeal, boolean hasBoneMeal, boolean hasSapling) {
        // 开骨粉但没骨粉：停止一切 (不种也不催熟)，等待骨粉补充
        // —— 防止"只种不催熟"导致树苗只消耗不长成，树苗逐渐耗尽
        if (useBoneMeal && !hasBoneMeal) return Action.NONE;
        if (upIsSapling) {
            // 已种下 → 骨粉催熟（若开启且有骨粉）
            if (useBoneMeal && hasBoneMeal) return Action.BONEMEAL;
            return Action.NONE;
        }
        if (upIsAirOrReplaceable && hasSapling) return Action.PLANT;
        return Action.NONE;
    }
}
