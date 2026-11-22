package com.fo.addon.ancient.core;

/**
 * 附魔之瓶等消耗品的补给判定（纯逻辑，可单测）。
 *
 * <p>口径来自方案：最低 32 瓶、目标 192 瓶；低于最低值先补给，补给数量按「补到目标」计算。
 */
public final class GearSupplyLogic {

    private GearSupplyLogic() {
    }

    /** 当前数量是否低于最低值（严格小于；31 需要补给，32 不需要）。 */
    public static boolean needsSupply(int current, int min) {
        return current < Math.max(0, min);
    }

    /** 要从仓库取出多少才能到目标数量（不会为负）。 */
    public static int amountToWithdraw(int current, int target) {
        return Math.max(0, target - Math.max(0, current));
    }

    /** 这批最多还能投多少瓶（受本次上限与剩余瓶数共同约束）。 */
    public static int bottlesToUse(int bottlesLeft, int maxPerSession, int usedThisSession) {
        int leftInSession = Math.max(0, maxPerSession - Math.max(0, usedThisSession));
        return Math.min(Math.max(0, bottlesLeft), leftInSession);
    }

    /** 是否具备开始修复的最低瓶数（低于最低值先补给，不带着几瓶硬修）。 */
    public static boolean canStartRepair(int bottles, int min) {
        return bottles >= Math.max(0, min);
    }

    /** 需要补给的目标数量是否合理（目标必须大于最低值，否则只补到最低值）。 */
    public static int effectiveTarget(int min, int target) {
        return Math.max(Math.max(0, min), Math.max(0, target));
    }

    /**
     * 修复时主手/副手怎么放。
     *
     * @param mainHandPickaxe 设置项「修复时主手镐」是否为开
     * @return true = 主手镐、副手瓶（模式 A）；false = 主手瓶、副手镐（模式 B）
     */
    public static boolean useOffhandBottle(boolean mainHandPickaxe) {
        return mainHandPickaxe;
    }
}
