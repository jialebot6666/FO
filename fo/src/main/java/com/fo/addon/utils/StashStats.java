package com.fo.addon.utils;

/**
 * 搬运统计（只记本次在线，不落盘）。
 *
 * <p>口径（用户确认）：
 * <ul>
 *   <li>只统计本次在线；关模块<b>不清零</b>（搬一半排查问题，数字还在）；</li>
 *   <li>断线 / 退服（GameLeftEvent）由模块调用 {@link #reset()} 清零；</li>
 *   <li>关闭客户端内存自然释放；</li>
 *   <li>设置页「重置」按钮随时手动清零。</li>
 * </ul>
 *
 * <p>统计项：趟数（每次完整卸货 +1）+ 潜影盒数（拿取时按堆数量累加）。
 */
public final class StashStats {

    private static final StashStats INSTANCE = new StashStats();

    private int trips;
    private int shulkers;

    private StashStats() {
    }

    public static StashStats get() {
        return INSTANCE;
    }

    public void addTrip() {
        trips++;
    }

    public void addShulkers(int count) {
        if (count > 0) shulkers += count;
    }

    public int trips() {
        return trips;
    }

    public int shulkers() {
        return shulkers;
    }

    public void reset() {
        trips = 0;
        shulkers = 0;
    }

    /** 设置页显示文本。 */
    public String displayText() {
        return trips + " 趟 · " + shulkers + " 个潜影盒";
    }
}
