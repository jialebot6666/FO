package com.fo.addon.utils;

/**
 * 飞行阶段烟花使用判定（对齐 Ying：巡航段正常飞行**不烧烟花**，只有「无鞘翅紧急拉升」才按间隔补）。
 * 纯逻辑，不依赖 MC 运行时，可单元测试。
 */
public final class FlightFireworkLogic {
    private FlightFireworkLogic() {
    }

    /**
     * 是否该使用烟花：
     * - 紧急拉升（frameAirEmpty 拉升到 max-height 以上）：必须用（防止速度掉光坠落）——当前唯一的调用场景
     * - 爬升阶段（climbing=true）：按间隔使用（保留该分支以便复用判定；Ying 原版巡航段不调用它）
     * - 滑翔/下滑阶段（climbing=false）：不使用
     *
     * @param climbing        当前是否爬升
     * @param emergencyClimb  紧急拉升（空中检查无鞘翅时的拉升分支）
     * @param stateTick       当前阶段已持续 tick
     * @param lastFireworkTick 上次使用烟花的 tick
     * @param intervalTicks   烟花间隔（tick）
     */
    public static boolean shouldUseFirework(boolean climbing, boolean emergencyClimb, int stateTick, int lastFireworkTick, int intervalTicks) {
        if (emergencyClimb) {
            return stateTick - lastFireworkTick >= Math.max(1, intervalTicks);
        }
        return climbing && stateTick - lastFireworkTick >= Math.max(1, intervalTicks);
    }
}
