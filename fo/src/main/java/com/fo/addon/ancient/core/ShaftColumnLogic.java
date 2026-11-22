package com.fo.addon.ancient.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 竖井列生成与井口判定（纯逻辑，可单测）。
 *
 * <p>铁律：竖井列绝不取箱子正上方。列在箱子水平外围的固定偏移上，
 * 默认偏移 2~4 格，四个正方向各一列。
 */
public final class ShaftColumnLogic {

    /** 一列竖井（水平坐标 + 离箱子的偏移量）。 */
    public record Column(int x, int z, int offset) {
    }

    private ShaftColumnLogic() {
    }

    /**
     * 生成候选竖井列。
     *
     * <p>顺序：先按偏移从近到远，再按北/南/西/东。模块会再按玩家距离排序，
     * 这里只保证「同一偏移的四个方向都覆盖、绝不含 (0,0)」。
     */
    public static List<Column> candidates(int chestX, int chestZ, int minOffset, int maxOffset) {
        int min = Math.max(1, Math.min(minOffset, maxOffset));
        int max = Math.max(min, Math.max(minOffset, maxOffset));
        List<Column> out = new ArrayList<>();
        for (int off = min; off <= max; off++) {
            out.add(new Column(chestX, chestZ - off, off)); // 北
            out.add(new Column(chestX, chestZ + off, off)); // 南
            out.add(new Column(chestX - off, chestZ, off)); // 西
            out.add(new Column(chestX + off, chestZ, off)); // 东
        }
        return out;
    }

    /** 该列是否在箱子正上方（同一 (x,z)）。 */
    public static boolean isAboveChest(int chestX, int chestZ, Column column) {
        return column != null && column.x() == chestX && column.z() == chestZ;
    }

    /**
     * 井口是否可用。
     *
     * @param topSolid  井口脚下是实心方块
     * @param feetAir   井口两格高的第一格是空气
     * @param headAir   井口两格高的第二格是空气
     * @param fluidAtTop 井口或紧邻有流体
     */
    public static boolean surfaceOk(boolean topSolid, boolean feetAir, boolean headAir, boolean fluidAtTop) {
        return topSolid && feetAir && headAir && !fluidAtTop;
    }

    /** 按离玩家的水平距离排序（近的优先），不改变原列表。 */
    public static List<Column> sortedByDistance(List<Column> columns, double px, double pz) {
        List<Column> out = new ArrayList<>(columns);
        out.sort((a, b) -> Double.compare(dist2(a, px, pz), dist2(b, px, pz)));
        return out;
    }

    private static double dist2(Column c, double px, double pz) {
        double dx = c.x() + 0.5 - px;
        double dz = c.z() + 0.5 - pz;
        return dx * dx + dz * dz;
    }
}
