package com.fo.addon.ancient.core;

/**
 * 一次「鞘翅 + 镐子」修复会话的停止条件（纯逻辑，可单测）。
 *
 * <p>修复会话从投第一瓶开始，到两件都达到目标、瓶数用完、或出现安全风险为止。
 * 所有边界都在这里固定，模块只负责按结果执行动作。
 */
public final class RepairSessionLogic {

    /** 会话下一步。 */
    public enum Next {
        /** 继续投瓶。 */
        CONTINUE,
        /** 两件都修到目标，正常结束。 */
        DONE,
        /** 本次瓶数达到上限，停止但不算失败。 */
        STOP_LIMIT,
        /** 瓶子用完，需要补给后重开会话。 */
        STOP_NO_BOTTLES,
        /** 有装备低于最低安全线，立即停止并换备用。 */
        STOP_SAFETY
    }

    private RepairSessionLogic() {
    }

    /** 两件都达到目标。 */
    public static boolean complete(boolean elytraDone, boolean pickaxeDone) {
        return elytraDone && pickaxeDone;
    }

    /** 本次投瓶数达到上限。 */
    public static boolean limitReached(int used, int max) {
        return used >= Math.max(1, max);
    }

    /**
     * 计算下一步。
     *
     * @param elytraDone  鞘翅是否已达到修复目标
     * @param pickaxeDone 镐子是否已达到修复目标
     * @param used        本次已投瓶数
     * @param max         本次最多投瓶数
     * @param bottlesLeft 背包剩余瓶数
     * @param elytraSafe  鞘翅是否仍在最低安全线以上
     * @param pickaxeSafe 镐子是否仍在最低安全线以上
     */
    public static Next decide(boolean elytraDone, boolean pickaxeDone, int used, int max,
                              int bottlesLeft, boolean elytraSafe, boolean pickaxeSafe) {
        if (!elytraSafe || !pickaxeSafe) return Next.STOP_SAFETY;
        if (complete(elytraDone, pickaxeDone)) return Next.DONE;
        if (limitReached(used, max)) return Next.STOP_LIMIT;
        if (bottlesLeft <= 0) return Next.STOP_NO_BOTTLES;
        return Next.CONTINUE;
    }

    /** 是否有装备需要修复（用于决定要不要开修复会话）。 */
    public static boolean anyNeedsRepair(boolean elytraNeeds, boolean pickaxeNeeds) {
        return elytraNeeds || pickaxeNeeds;
    }
}
