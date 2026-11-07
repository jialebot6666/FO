package com.fo.addon.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动搬运专用的「箱子旁边落脚点」生成与排序（V6.7p25，纯逻辑，可单测）。
 *
 * <p><b>为什么不复用 {@link StandSpotLogic}</b>：那是自动挖沙开潜影盒用的（半径 1、Y 只到盒子上一层），
 * 两边用途和半径都不一样 —— 搬运要的是「离箱子 1~3 格都能隔着方块开箱」，混用以后改一个会误伤另一个。
 * 所以这里单独一套，互不引用。
 *
 * <p>生成规则（用户口径：<b>绝不能走到箱子正上方</b>）：
 * <ul>
 *   <li>水平只取「本圈」1 → 2 → 3 格，逐圈外扩（近的优先被选到）；</li>
 *   <li>Y 只取 {@code chestY} 与 {@code chestY+1}，<b>不取 chestY-1</b>（站到箱子下面开不了）；</li>
 *   <li>dx、dz 同时为 0 的列直接跳过 —— 箱子自身那一列（含正上方）根本不是候选。</li>
 * </ul>
 */
public final class StashChestSpotLogic {

    private StashChestSpotLogic() {
    }

    /** 落脚点搜索的最外圈半径（格）。原版交互距离 4.5，取 3 格留余量。 */
    public static final int MAX_RADIUS = 3;

    /**
     * 生成箱子周围的候选落脚点（脚所在格坐标）。
     *
     * @return 候选列表，每项 {x, y, z}；按 1→2→3 圈顺序排列
     */
    public static List<int[]> candidates(int chestX, int chestY, int chestZ) {
        List<int[]> out = new ArrayList<>();
        for (int r = 1; r <= MAX_RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;   // 只扫本圈
                    out.add(new int[]{chestX + dx, chestY, chestZ + dz});       // 与箱子同层
                    out.add(new int[]{chestX + dx, chestY + 1, chestZ + dz});   // 站在上一格
                }
            }
        }
        return out;
    }

    /**
     * 候选排序键（越小越优先）：<b>有视线 &gt; 离得近 &gt; 同层</b>。
     *
     * <p>视线权重必须压过距离：用户口径是「优先选有视线的箱子」，哪怕多走几格。
     * 同视线下按到玩家的平方距离排；再同则同层（{@code dy=0}）优先于上一层。
     *
     * @param hasLos  从该落脚点能不能直视箱子
     * @param distSq  该落脚点到玩家的平方距离
     * @param dy      该落脚点相对箱子的高度差（0 或 +1）
     */
    /**
     * 从候选里挑「离箱子中心最近」的一个（V6.7p28：点不开时再靠近一格用）。
     *
     * <p>按 3D 平方距离算，同层格优先于上一层（同水平距离时 {@code dy=0} 更近）。
     *
     * @return 最近的候选；候选为空时返回 null
     */
    public static int[] nearestToChest(int chestX, int chestY, int chestZ, List<int[]> cands) {
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int[] c : cands) {
            double dx = (c[0] + 0.5) - (chestX + 0.5);
            double dy = (c[1] + 0.5) - (chestY + 0.5);
            double dz = (c[2] + 0.5) - (chestZ + 0.5);
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    public static double score(boolean hasLos, double distSq, int dy) {
        double s = hasLos ? 0.0 : 1_000_000.0;   // 没视线的排到最后，但仍然可用（发包开箱）
        s += distSq;
        if (dy != 0) s += 4.0;                    // 同层优先，避免没事往上爬
        return s;
    }
}
