package com.fo.addon.elytra.core;

/**
 * Baritone「段失败」计数窗口（纯逻辑，不依赖 MC 运行时，可单元测试）。
 *
 * <p>背景：Baritone 连续报 {@code Failed to compute/recompute segment} 时说明它已经算不出路线了，
 * 需要在一个很短的窗口内累计失败条数，超过阈值就把鞘翅进程重置掉。</p>
 *
 * <p>窗口按 tick 记：两条失败消息的间隔超过 {@link #WINDOW} tick 就认为开始了新的一轮，计数从 1 重来。
 * 边界语义与原实现一致 —— <b>间隔恰好等于 WINDOW 仍算同一轮</b>，只有严格大于才算过期。</p>
 *
 * <p>{@link #clear()} 只清计数，不清 tick 时钟（与原实现一致，避免复位后窗口判定失真）。</p>
 */
public final class SegFailWindow {
    /** 段失败计数窗口（tick）。 */
    public static final int WINDOW = 6;

    private int tick;
    private int count;
    private int lastTick = Integer.MIN_VALUE / 2;
    private boolean resetRequested;

    /** 每 tick 调用一次，推进窗口时钟。 */
    public void markTick() {
        tick++;
    }

    /** 记一条段失败消息。 */
    public void recordFailure() {
        if (tick - lastTick > WINDOW) {
            count = 1;
        } else {
            count++;
        }
        lastTick = tick;
        resetRequested = true;
    }

    /** 当前窗口内的失败条数；窗口已过期则为 0。 */
    public int count() {
        return tick - lastTick > WINDOW ? 0 : count;
    }

    /** 取走一次「需要重置鞘翅进程」的请求（取走后清零，不会重复触发）。 */
    public boolean consumeResetRequest() {
        if (!resetRequested) return false;
        resetRequested = false;
        return true;
    }

    /** 清空计数与待处理请求。 */
    public void clear() {
        count = 0;
        lastTick = Integer.MIN_VALUE / 2;
        resetRequested = false;
    }
}
