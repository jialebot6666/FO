package com.fo.addon.utils;

/**
 * 巡航「上升顶点」检测：垂直速度由正转负的那一 tick 就是最高点（对齐 Pitch40Util 的
 * {@code goingUp && velocity.y <= 0} 判定）。
 *
 * <p>用途：抵达顶点时把巡航高度区间上抬（见 {@link CruiseBoundsLogic}），从而不掉高度、还能持续攀升。
 * 检测用「速度符号翻转」而不是「是否处于爬升段」——因为滞回区间会在到上界时就翻成滑翔段，
 * 而真正的最高点通常还在上界之上（惯性继续上冲），用爬升段判定会漏掉。
 *
 * <p>本类为纯逻辑，不依赖 MC 运行时，可单元测试。
 */
public final class CruiseApexDetector {
    private boolean rising = false;

    /**
     * 推进一 tick。
     *
     * @param velocityY 当前垂直速度（格/tick，正 = 上升）
     * @return true 表示本 tick 恰为上升顶点（由上升转为不再上升）
     */
    public boolean update(double velocityY) {
        if (velocityY > 0) {
            rising = true;
            return false;
        }
        if (rising) {
            rising = false;
            return true;
        }
        return false;
    }

    /** 重置内部状态（退出巡航 / 换船时调用，避免残留的 rising 误判一个假的顶点）。 */
    public void reset() {
        rising = false;
    }
}
