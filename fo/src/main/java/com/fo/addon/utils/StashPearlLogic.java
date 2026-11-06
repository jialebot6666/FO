package com.fo.addon.utils;

/**
 * 珍珠相关纯逻辑（可单测，不碰 MC 运行时）。
 *
 * <p>三号模式的珍珠补货规则（用户口径）：
 * 珍珠箱全路线共用一个；手上珍珠 &lt; 阈值（默认 4）时去补，
 * <b>精确补到目标值</b>（默认 16，即一整堆），不是整堆搬；取出来的珍珠不还回去。
 */
public final class StashPearlLogic {

    private StashPearlLogic() {
    }

    /** 是否需要补货：低于阈值就补。 */
    public static boolean needsRestock(int current, int threshold) {
        return current < threshold;
    }

    /**
     * 需要补多少颗：不需要补返回 0；需要补则返回「补到 target 的缺口」。
     * 例：current=6、threshold=4、target=16 → 0（6 已高于阈值）；
     * current=3 → 13。
     */
    public static int missingToTarget(int current, int threshold, int target) {
        if (!needsRestock(current, threshold)) return 0;
        return Math.max(0, target - current);
    }

    /**
     * 本次开箱子实际要拿几颗（受箱子里这一堆剩余数量限制）。
     */
    public static int takeNow(int missing, int chestStackCount) {
        if (missing <= 0 || chestStackCount <= 0) return 0;
        return Math.min(missing, chestStackCount);
    }

    /**
     * 物品 ID 是否潜影盒（含所有染色变体）。
     * 用完整 ID 的 "shulker_box" 片段判断——原版没有任何其他物品 ID 含这个片段，
     * 比 misaka 的 contains("shulker") 精确。
     */
    public static boolean isShulkerBoxId(String itemId) {
        return itemId != null && itemId.contains("shulker_box");
    }

    /** 坐标 → "x y z"（珍珠点映射表的存储格式）。 */
    public static String formatPos(int x, int y, int z) {
        return x + " " + y + " " + z;
    }

    /**
     * 到珍珠点算不算「到了」（V6.7p20）。
     *
     * <p>为什么不再用单一的 3D 距离：寻路用的是有 Y 参与的目标（GoalNear），
     * 必须和点击判定保持同一套尺度；以前 3D 距离 ≤4 配 GoalXZ（只认 XZ），
     * 会出现「寻路说到了、点击说没到」的来回抖。
     *
     * @param dx            玩家格 - 珍珠点格 的 X 差
     * @param dy            Y 差
     * @param dz            Z 差
     * @param maxHorizontal 允许的水平距离（格）
     * @param maxDy         允许的高度差（格）
     */
    public static boolean nearPearlPoint(double dx, double dy, double dz,
                                         double maxHorizontal, double maxDy) {
        return Math.hypot(dx, dz) <= maxHorizontal && Math.abs(dy) <= maxDy;
    }

    /** "x y z" → 三个整数；格式不对返回 null。 */
    public static int[] parsePos(String text) {
        if (text == null) return null;
        String[] parts = text.trim().split("\\s+");
        if (parts.length != 3) return null;
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
