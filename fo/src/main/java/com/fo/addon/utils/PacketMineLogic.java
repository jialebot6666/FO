package com.fo.addon.utils;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * FO 发包挖掘——纯逻辑（可单测，不依赖 Minecraft 运行时状态）。
 *
 * <p>移植自 Hachimi V2 反编译源码 {@code pw.hachimi.client.module.combat.AutoMine} 的
 * 目标方块计算逻辑（原混淆方法 {@code art()} / {@code aru()} / surround 扫描段），
 * 按"只挖实心可破坏方块"的明确语义重写：
 * 原版对 Surround 邻位还包含"空气位"候选（依赖其私有 Multitask 状态，语义无法从反编译
 * 代码完全确认），移植版刻意不保留该行为——挖空气没有意义，避免引入未验证的行为。</p>
 *
 * <p>三个候选来源（与 Hachimi 原版一致）：
 * <ol>
 *   <li><b>Burrow（脚里）</b>：目标脚 Y 平面上，以脚坐标为中心 ±0.25 的四个角所在方块
 *       （覆盖"跨方块边界站立"时脚里方块不在 {@code getBlockPos()} 的情况）。</li>
 *   <li><b>Face（头顶）</b>：目标 y+1.5 平面上同规则的四个角所在方块（头里方块）。</li>
 *   <li><b>Surround（围城）</b>：目标包围盒脚层覆盖的所有地板方块。</li>
 * </ol></p>
 */
public final class PacketMineLogic {
    private PacketMineLogic() {
    }

    /** Burrow（脚里）候选：脚 Y 平面四角 ±0.25 + getBlockPos 本体，去重。 */
    public static List<BlockPos> burrowCandidates(double x, double y, double z) {
        List<BlockPos> out = new ArrayList<>();
        addIfNew(out, BlockPos.ofFloored(x + 0.25, y, z + 0.25));
        addIfNew(out, BlockPos.ofFloored(x - 0.25, y, z - 0.25));
        addIfNew(out, BlockPos.ofFloored(x + 0.25, y, z - 0.25));
        addIfNew(out, BlockPos.ofFloored(x - 0.25, y, z + 0.25));
        addIfNew(out, BlockPos.ofFloored(x, y, z));
        return out;
    }

    /** Face（头顶）候选：y+1.5 平面四角 ±0.25，去重。 */
    public static List<BlockPos> faceCandidates(double x, double y, double z) {
        double headY = y + 1.5;
        List<BlockPos> out = new ArrayList<>();
        addIfNew(out, BlockPos.ofFloored(x + 0.25, headY, z + 0.25));
        addIfNew(out, BlockPos.ofFloored(x - 0.25, headY, z - 0.25));
        addIfNew(out, BlockPos.ofFloored(x + 0.25, headY, z - 0.25));
        addIfNew(out, BlockPos.ofFloored(x - 0.25, headY, z + 0.25));
        return out;
    }

    /**
     * Surround（围城）候选：实体包围盒脚层覆盖的地板方块。
     * 收缩半格再取 floor 的两角，覆盖"跨边界站立"时的 2x2 地板组合。
     */
    public static List<BlockPos> surroundCandidates(double x, double y, double z, double width) {
        double half = width / 2.0 + 0.05;
        double feetY = Math.floor(y);
        List<BlockPos> out = new ArrayList<>();
        for (double sx : new double[]{x - half, x + half}) {
            for (double sz : new double[]{z - half, z + half}) {
                addIfNew(out, BlockPos.ofFloored(Math.floor(sx), feetY, Math.floor(sz)));
            }
        }
        return out;
    }

    /**
     * 从候选里挑一个最优方块（默认策略：离玩家水平距离最近）。
     * 距离用水平平方距离（与 Y 无关），对齐 Hachimi 原版按 {@code pos.getSquaredDistance(playerPos)}
     * 排序但剔除站立高度差带来的偏差。
     */
    public static BlockPos closest(List<BlockPos> candidates, double px, double pz) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : candidates) {
            double dx = pos.getX() + 0.5 - px;
            double dz = pos.getZ() + 0.5 - pz;
            double dist = dx * dx + dz * dz;
            if (dist < bestDist) {
                bestDist = dist;
                best = pos;
            }
        }
        return best;
    }

    private static void addIfNew(List<BlockPos> out, BlockPos pos) {
        if (!out.contains(pos)) out.add(pos);
    }
}
