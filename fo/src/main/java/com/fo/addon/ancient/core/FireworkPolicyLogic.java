package com.fo.addon.ancient.core;

/**
 * 补烟花判定（纯逻辑，可单测）。
 *
 * <p>只有「正在抬头且垂直速度不够」或「已经低于最低高度」才补，
 * 俯冲段一律不补，避免看起来「一直放烟花」。
 */
public final class FireworkPolicyLogic {

    private FireworkPolicyLogic() {
    }

    /**
     * 巡航段专用：抬头爬升不再补烟花，只保「掉到硬下限以下」这一条兜底。
     */
    public static boolean shouldBoostHolding(double y, double floorY, double distToTarget, int cooldownTicks) {
        if (cooldownTicks > 0) return false;
        if (distToTarget <= 20.0) return false;
        return y < floorY;
    }

    public static boolean shouldBoost(boolean climbing, double y, double minY, double maxY,
                                      double distToTarget, double vy, int cooldownTicks) {
        if (cooldownTicks > 0) return false;
        if (distToTarget <= 20.0) return false;
        if (y >= maxY) return false;
        if (climbing && vy < -0.35) return true;
        return y < minY;
    }
}
