package com.fo.addon.utils;

/**
 * 巡航飞行烟花使用判定（对齐 Ying：爬升阶段用烟花加速，滑翔/下滑阶段不烧烟花省消耗）。
 * 纯逻辑，不依赖 MC 运行时，可单元测试。
 */
public final class FlightFireworkLogic {
    private FlightFireworkLogic() {
    }

    /**
     * 巡航段（FLYING）是否该使用烟花：
     * - 爬升阶段（climbing=true）：按烟花间隔使用（加速爬升，防止掉速坠落）
     * - 滑翔/下滑阶段（climbing=false）：不使用（对齐 Ying "滑翔阶段不使用烟花，降到最低高度后重新爬升"）
     * - 紧急拉升（frameAirEmpty 拉升到 max-height 以上）：必须用（防止速度掉光坠落）
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
