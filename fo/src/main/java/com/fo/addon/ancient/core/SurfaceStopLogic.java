package com.fo.addon.ancient.core;

/**
 * 地表停靠要做什么（纯逻辑，可单测）。
 *
 * <p>触发条件按方案：满 64、鞘翅/镐子低于修复阈值、经验瓶低于最低值、背包空间不足。
 * 一次停靠可以同时做多件事，所以用三个布尔而不是单个枚举。
 */
public final class SurfaceStopLogic {

    /** 本次停靠需要执行的动作。 */
    public record Needs(boolean pack, boolean supply, boolean repair) {
        public boolean any() {
            return pack || supply || repair;
        }

        public static Needs none() {
            return new Needs(false, false, false);
        }
    }

    private SurfaceStopLogic() {
    }

    /**
     * 是否需要存盒。
     *
     * @param apples       背包/潜影盒里累计的附魔金数量
     * @param target       目标数量（默认 64）
     * @param inventoryFull 背包是否已满
     */
    public static boolean shouldPack(int apples, int target, boolean inventoryFull) {
        return apples >= Math.max(1, target) || inventoryFull;
    }

    /** 是否需要修复（任一装备低于阈值）。 */
    public static boolean shouldRepair(boolean elytraNeedsRepair, boolean pickaxeNeedsRepair) {
        return elytraNeedsRepair || pickaxeNeedsRepair;
    }

    /** 是否需要补给（经验瓶低于最低值，或烟花/食物不足）。 */
    public static boolean shouldSupply(boolean bottlesLow, boolean fireworksLow, boolean foodLow) {
        return bottlesLow || fireworksLow || foodLow;
    }

    /** 汇总。 */
    public static Needs evaluate(int apples, int target, boolean inventoryFull,
                                 boolean elytraNeedsRepair, boolean pickaxeNeedsRepair,
                                 boolean bottlesLow, boolean fireworksLow, boolean foodLow) {
        return new Needs(
            shouldPack(apples, target, inventoryFull),
            shouldSupply(bottlesLow, fireworksLow, foodLow),
            shouldRepair(elytraNeedsRepair, pickaxeNeedsRepair)
        );
    }
}
