package com.fo.addon.ancient.core;

/**
 * 附魔金采集的装备耐久判定（纯逻辑，可单测）。
 *
 * <p>只做「要不要修 / 能不能修 / 要不要换备用」的决策，不碰任何 MC 运行时。
 * 经验修补的修复动作由模块负责，这里保证边界条件固定、可回归。
 */
public final class GearRepairLogic {

    /** 一件装备在当前状态下该做什么。 */
    public enum Action {
        /** 不需要处理。 */
        NONE,
        /** 有经验修补且经验瓶足够，可以投瓶修复。 */
        REPAIR,
        /** 没有经验修补或低于最低安全线，但有备用装备，换备用。 */
        REPLACE_SPARE,
        /** 修不了也换不了，必须暂停。 */
        PAUSE
    }

    private GearRepairLogic() {
    }

    /** 剩余耐久点数；maxDamage <= 0 视为不可损坏，返回 0。 */
    public static int remaining(int maxDamage, int damage) {
        if (maxDamage <= 0) return 0;
        return Math.max(0, maxDamage - Math.max(0, damage));
    }

    /** 剩余耐久百分比（0~100）；maxDamage <= 0 视为 100。 */
    public static double remainingPercent(int maxDamage, int damage) {
        if (maxDamage <= 0) return 100.0;
        double pct = remaining(maxDamage, damage) * 100.0 / maxDamage;
        return Math.max(0.0, Math.min(100.0, pct));
    }

    /** 是否低于修复阈值（严格小于；20% 整不触发）。 */
    public static boolean needsRepair(int maxDamage, int damage, double thresholdPercent) {
        if (maxDamage <= 0) return false;
        return remainingPercent(maxDamage, damage) < thresholdPercent;
    }

    /** 是否已经修到目标（大于等于；90% 整即停止）。 */
    public static boolean repairDone(int maxDamage, int damage, double targetPercent) {
        if (maxDamage <= 0) return true;
        return remainingPercent(maxDamage, damage) >= targetPercent;
    }

    /** 是否低于最低安全耐久（低于此值禁止继续投瓶，先换备用）。 */
    public static boolean belowSafety(int maxDamage, int damage, int minRemaining) {
        if (maxDamage <= 0) return false;
        return remaining(maxDamage, damage) < Math.max(1, minRemaining);
    }

    /**
     * 单件装备的处置决策。
     *
     * <p>顺序很重要：<b>先保命再修复</b>。低于最低安全线时即使有经验修补也不投瓶
     * （装备随时可能碎），优先换备用；没有备用才暂停。
     *
     * @param maxDamage        最大耐久；<=0 表示不可损坏
     * @param damage           已损失耐久
     * @param thresholdPercent 修复阈值百分比
     * @param minRemaining     最低安全耐久点数
     * @param mending          是否带经验修补
     * @param spareAvailable   是否有可替换的备用装备
     * @param bottlesAvailable 是否有足够的附魔之瓶
     */
    public static Action decide(int maxDamage, int damage, double thresholdPercent, int minRemaining,
                                boolean mending, boolean spareAvailable, boolean bottlesAvailable) {
        if (maxDamage <= 0) return Action.NONE;
        if (belowSafety(maxDamage, damage, minRemaining)) {
            return spareAvailable ? Action.REPLACE_SPARE : Action.PAUSE;
        }
        if (!needsRepair(maxDamage, damage, thresholdPercent)) return Action.NONE;
        if (!mending) {
            return spareAvailable ? Action.REPLACE_SPARE : Action.PAUSE;
        }
        if (!bottlesAvailable) return Action.PAUSE;
        return Action.REPAIR;
    }
}
