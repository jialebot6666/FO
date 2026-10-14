package com.fo.addon.utils;

/**
 * 巡航高度区间（对齐 miles352/meteor-stashhunting-addon 的 Pitch40Util#resetBounds 与
 * Meteor ElytraFly 的 pitch40-lower-bounds / pitch40-upper-bounds 语义）。
 *
 * <p>固定区间（180~220）只能来回振荡；把区间「重置到当前高度下方」并跟着爬升顶点上抬，
 * 才能实现参考实现那种「不掉高度、还会持续攀升」的效果。
 *
 * <p>本类为纯逻辑，不依赖 MC 运行时，可单元测试。
 */
public final class CruiseBoundsLogic {
    /** 上界相对当前高度的下移量（对齐 Pitch40Util：上界 = 当前 Y - 5）。 */
    public static final double APEX_OFFSET = 5.0;

    /** 掉到下界以下多少格，判定为「需要把区间重置到当前位置」（对齐 Pitch40Util 的 -10）。 */
    public static final double FALL_MARGIN = 10.0;

    /** 上界硬上限。MC 建筑高度上限 320，留 20 格余量，避免区间被抬到世界外面。 */
    public static final double MAX_UPPER = 300.0;

    private CruiseBoundsLogic() {
    }

    /**
     * 由「当前高度」算出新的上界：当前 Y - {@link #APEX_OFFSET}，并且不超过 {@link #MAX_UPPER}。
     */
    public static double upperFrom(double y) {
        return Math.min(y - APEX_OFFSET, MAX_UPPER);
    }

    /**
     * 由上界与区间间隔算出下界。
     */
    public static double lowerFrom(double upper, double gap) {
        return upper - gap;
    }

    /**
     * 是否该把区间重置到当前位置：已掉到下界以下 {@code margin} 格（说明在持续下沉）。
     */
    public static boolean shouldResetOnFall(double y, double lowerBound, double margin) {
        return y <= lowerBound - margin;
    }
}
