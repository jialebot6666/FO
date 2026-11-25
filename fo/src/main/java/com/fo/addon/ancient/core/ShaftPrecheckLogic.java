package com.fo.addon.ancient.core;

import java.util.List;

/**
 * 井道预检（纯逻辑，可单测）。
 *
 * <p>2.0 版的竖井开在「目标箱子正上方」，下挖前一次性扫以箱子为中心的 5 列：
 * 正下方 1 列 + 前后左右各 1 列。任一一列见流体 / 不可破 / 尖啸体 / 落沙就拉黑该箱，
 * 换下一个箱子；只见到紫水晶母岩/紫水晶簇不算拉黑（挖井时顺手挖掉，否则会挡升井）。
 */
public final class ShaftPrecheckLogic {

    /** 单列的阻断类型。 */
    public enum Blocker {
        /** 干净。 */
        NONE,
        /** 水 / 岩浆。 */
        FLUID,
        /** 基岩 / 强化深板岩等挖不动的方块。 */
        UNBREAKABLE,
        /** 幽匿尖啸体（会召唤监守者）。 */
        SHRIEKER,
        /** 沙子 / 沙砾 / 混凝土粉末（可能塌方）。 */
        FALLING,
        /** 紫水晶母岩 / 紫水晶簇：能挖，但必须清掉。 */
        AMETHYST
    }

    /** 一列的扫描结论：最严重的阻断类型 + 出现位置（Y）。 */
    public record ColumnScan(Blocker blocker, int y) {
        public static ColumnScan clean() {
            return new ColumnScan(Blocker.NONE, 0);
        }
    }

    /** 5 列预检的总体结论。 */
    public record Verdict(boolean pass, Blocker blocker, String reason, int blockerY) {
        public static Verdict ok() {
            return new Verdict(true, Blocker.NONE, "", 0);
        }
    }

    private ShaftPrecheckLogic() {
    }

    /** 单列内多个阻断取最严重的一个（顺序即优先级）。 */
    public static Blocker worst(List<Blocker> blockers) {
        Blocker worst = Blocker.NONE;
        if (blockers == null) return worst;
        for (Blocker b : blockers) {
            if (b == null || b == Blocker.NONE) continue;
            if (severity(b) > severity(worst)) worst = b;
        }
        return worst;
    }

    /**
     * 5 列一起判定。
     *
     * @param center 正下方那一列
     * @param north  北侧一列（z-1）
     * @param south  南侧一列（z+1）
     * @param west   西侧一列（x-1）
     * @param east   东侧一列（x+1）
     */
    public static Verdict evaluate(ColumnScan center, ColumnScan north, ColumnScan south,
                                   ColumnScan west, ColumnScan east) {
        ColumnScan[] all = {center, north, south, west, east};
        String[] names = {"正下方", "北侧", "南侧", "西侧", "东侧"};
        for (int i = 0; i < all.length; i++) {
            ColumnScan c = all[i];
            if (c == null || c.blocker() == null || c.blocker() == Blocker.NONE) continue;
            if (c.blocker() == Blocker.AMETHYST) continue; // 能挖掉，不拉黑
            return new Verdict(false, c.blocker(),
                names[i] + "第 " + (c.y() >= 0 ? c.y() : 0) + " 层有" + text(c.blocker()) + "，拉黑该箱",
                c.y());
        }
        return Verdict.ok();
    }

    /** 判定用中文原因。 */
    public static String text(Blocker b) {
        return switch (b) {
            case FLUID -> "水/岩浆";
            case UNBREAKABLE -> "挖不动的方块";
            case SHRIEKER -> "幽匿尖啸体";
            case FALLING -> "会塌方的方块";
            case AMETHYST -> "紫水晶";
            case NONE -> "无";
        };
    }

    private static int severity(Blocker b) {
        return switch (b) {
            case NONE -> 0;
            case AMETHYST -> 1;
            case FALLING -> 2;
            case SHRIEKER -> 3;
            case UNBREAKABLE -> 4;
            case FLUID -> 5;
        };
    }
}
