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

    /**
     * 巡航段「按需补烟花」判定（对齐 miles352/meteor-stashhunting-addon 的 Pitch40Util auto-firework）：
     * 不再按固定时间间隔烧，而是**只在垂直速度真的掉下来**时补一发，所以绝大多数时间一个都不烧。
     *
     * <p>四个条件同时成立才补：
     * <ol>
     *   <li>开关已开；</li>
     *   <li>处于**爬升段**——滑翔段本来就在下沉（速度恒为负），不限制的话整段滑翔都会被判定成「该补」而烧光烟花；</li>
     *   <li>冷却已过；</li>
     *   <li>还没到上界，且垂直速度低于阈值（开始掉速 / 转下沉）。</li>
     * </ol>
     *
     * @param enabled     「巡航按需补烟花」开关
     * @param climbing    是否处于爬升段
     * @param velocityY   当前垂直速度（格/tick）
     * @param threshold   速度阈值（默认 -0.05）
     * @param y           当前高度
     * @param upperBound  巡航上界
     * @param cooldown    距上次补烟花的剩余冷却 tick（>0 表示还在冷却）
     */
    public static boolean shouldTopUpOnDemand(boolean enabled, boolean climbing, double velocityY,
                                              double threshold, double y, double upperBound, int cooldown) {
        if (!enabled) return false;
        if (!climbing) return false;
        if (cooldown > 0) return false;
        if (y >= upperBound) return false;
        return velocityY < threshold;
    }
}
