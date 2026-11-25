package com.fo.addon.ancient.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 肥箱优先（纯逻辑，可单测）。
 *
 * <p>搜索掷骰已经算出每箱附魔金的实际个数，采集端按「≥阈值的先飞」排序；
 * 数量未知（0，老存档）按瘦箱处理，排在后面。
 */
public final class FatChestLogic {

    private FatChestLogic() {
    }

    /** 是否肥箱：数量已知且 ≥ 阈值。 */
    public static boolean isFat(AncientChestStore.Mark mark, int threshold) {
        if (mark == null) return false;
        int t = Math.max(1, threshold);
        return mark.goldCount() > 0 && mark.goldCount() >= t;
    }

    /**
     * 肥箱优先排序：肥箱在前、瘦箱/未知在后，同档按离 (px,pz) 的水平距离。
     * 返回新列表，不修改入参。
     */
    public static List<AncientChestStore.Mark> preferFat(List<AncientChestStore.Mark> marks,
                                                          int threshold, double px, double pz) {
        List<AncientChestStore.Mark> out = new ArrayList<>();
        if (marks == null) return out;
        for (AncientChestStore.Mark m : marks) {
            if (m != null) out.add(m);
        }
        out.sort(Comparator
            .comparingInt((AncientChestStore.Mark m) -> isFat(m, threshold) ? 0 : 1)
            .thenComparingDouble(m -> distanceSq(m, px, pz)));
        return out;
    }

    public static double distanceSq(AncientChestStore.Mark m, double px, double pz) {
        double dx = m.x() + 0.5 - px;
        double dz = m.z() + 0.5 - pz;
        return dx * dx + dz * dz;
    }
}
