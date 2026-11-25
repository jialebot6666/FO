package com.fo.addon.ancient.core;

/**
 * 巡航高度滞回（纯逻辑，可单测）。
 *
 * <p>到了最高高度就停止抬头，掉到最低高度才重新抬头，中间保持上一次状态；
 * 这样俯仰不会在 230/300 边界来回翻。
 */
public final class FlightHysteresisLogic {

    /** 抬头俯仰。 */
    public static final float PITCH_UP = -54.77f;
    /** 低头俯仰。 */
    public static final float PITCH_DOWN = 37.72f;

    private FlightHysteresisLogic() {
    }

    public static boolean nextClimbing(double y, double minY, double maxY, boolean prev) {
        if (y >= maxY) return false;
        if (y <= minY) return true;
        return prev;
    }

    public static float pitch(boolean climbing) {
        return climbing ? PITCH_UP : PITCH_DOWN;
    }
}
