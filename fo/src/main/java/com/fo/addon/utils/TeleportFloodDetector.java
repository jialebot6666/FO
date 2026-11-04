package com.fo.addon.utils;

import java.util.ArrayDeque;

/**
 * 「卡脚」检测器（V6.7p9，思路来自 ZenithProxy futa 插件的 AntiStuck）。
 *
 * <p>原理：人一旦被卡住（挤在方块里、被水柱顶住、卡在活板门边沿），服务端会不停把你
 * 拉回原位，每拉一次就是一个位置校正包。正常跑图、被珍珠拉一次都不会到「5 秒 6 次」，
 * 到了基本就是卡死了。
 *
 * <p>本类只做计数与窗口裁剪，不碰 MC，可单测；调用方负责在客户端线程执行真正的脱困动作。
 * 方法全部 synchronized —— 网络线程会调 {@link #record(long)}。
 */
public final class TeleportFloodDetector {

    private final ArrayDeque<Long> times = new ArrayDeque<>();
    private final long windowMs;
    private final int threshold;

    /**
     * @param windowMs  统计窗口（毫秒）
     * @param threshold 窗口内达到多少次算「频繁传送」
     */
    public TeleportFloodDetector(long windowMs, int threshold) {
        if (windowMs <= 0) throw new IllegalArgumentException("windowMs must be > 0");
        if (threshold <= 0) throw new IllegalArgumentException("threshold must be > 0");
        this.windowMs = windowMs;
        this.threshold = threshold;
    }

    /**
     * 记录一次位置校正包。
     *
     * @param nowMs 当前时间（毫秒）
     * @return true = 窗口内次数已达阈值（判定卡脚）
     */
    public synchronized boolean record(long nowMs) {
        times.addLast(nowMs);
        trim(nowMs);
        return times.size() >= threshold;
    }

    /** 窗口内当前计数（只读，不新增记录）。 */
    public synchronized int count(long nowMs) {
        trim(nowMs);
        return times.size();
    }

    public synchronized void clear() {
        times.clear();
    }

    private void trim(long nowMs) {
        while (!times.isEmpty() && nowMs - times.peekFirst() > windowMs) {
            times.pollFirst();
        }
    }
}
