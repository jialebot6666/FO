package com.fo.addon.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 不祥宝库 ESP 的判定与筛选（纯逻辑，不依赖 Minecraft 运行时，可单测）。
 *
 * <p>背景：不祥宝库与普通宝库是 <b>同一个方块</b> {@code minecraft:vault}，
 * 只靠 blockstate 属性 {@code ominous} 区分（{@code ominous=true} 才是不祥宝库）。
 * 该属性随区块数据一起下发，所以客户端 ESP 在区块解析阶段就能判，
 * 不需要方块实体包，也不存在「服务器不下发」的问题。
 */
public final class VaultEspLogic {

    /** 默认扫描半径（格）：对齐服务器实际可渲染范围。 */
    public static final int DEFAULT_RADIUS = 500;

    /** 默认最多同时绘制的宝库数量。 */
    public static final int DEFAULT_LIMIT = 32;

    private VaultEspLogic() {
    }

    /**
     * 方块状态是否属于要找的那类：是宝库方块，且 ominous=true。
     *
     * @param isVault 方块是否为 {@code minecraft:vault}
     * @param ominous blockstate 的 ominous 属性
     */
    public static boolean matches(boolean isVault, boolean ominous) {
        return isVault && ominous;
    }

    /**
     * 「只显示未开」开关下，这个宝库该不该画。
     *
     * @param unopened     该宝库是否还没被打开过（vault_state=inactive）
     * @param onlyUnopened 开关是否打开
     */
    public static boolean shouldHighlight(boolean unopened, boolean onlyUnopened) {
        return !onlyUnopened || unopened;
    }

    /** 两个方块坐标的三维平方距离。 */
    public static double distanceSq(int x1, int y1, int z1, int x2, int y2, int z2) {
        double dx = (double) x1 - x2;
        double dy = (double) y1 - y2;
        double dz = (double) z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * 取半径内最近的若干命中点（按三维距离升序）。
     *
     * <p>距离以方块中心为准；超出半径的直接丢掉，供渲染阶段「只画近的、
     * 最多画 N 个」使用，避免上百个宝库同时绘制拖慢帧率。
     */
    public static List<Hit> nearest(List<Hit> hits, double px, double py, double pz, double radius, int limit) {
        if (hits == null || hits.isEmpty() || limit <= 0 || radius <= 0) {
            return List.of();
        }
        double r2 = radius * radius;
        List<Hit> inside = new ArrayList<>(hits.size());
        for (Hit hit : hits) {
            if (hit == null) continue;
            double dx = hit.x() + 0.5 - px;
            double dy = hit.y() + 0.5 - py;
            double dz = hit.z() + 0.5 - pz;
            if (dx * dx + dy * dy + dz * dz > r2) continue;
            inside.add(hit);
        }
        inside.sort(Comparator.comparingDouble(hit -> {
            double dx = hit.x() + 0.5 - px;
            double dy = hit.y() + 0.5 - py;
            double dz = hit.z() + 0.5 - pz;
            return dx * dx + dy * dy + dz * dz;
        }));
        if (inside.size() > limit) {
            return new ArrayList<>(inside.subList(0, limit));
        }
        return inside;
    }

    /**
     * 扫描时间片：本 tick 是否已用完时间预算。
     *
     * @param elapsedNanos 本 tick 已耗时（纳秒）
     * @param budgetNanos  本 tick 允许的耗时（纳秒）
     */
    public static boolean budgetSpent(long elapsedNanos, long budgetNanos) {
        return elapsedNanos >= budgetNanos;
    }

    /** 一个不祥宝库命中点。 */
    public record Hit(int x, int y, int z, boolean unopened) {
    }
}
