package com.fo.addon.ancient.core;

import net.minecraft.util.math.BlockPos;

/** 古城中一个符合条件的战利品箱子（移植自 misaka 古城模块）。 */
public final class FoundChest {
    public final BlockPos pos;
    public final double distance;
    public final String label;
    /** 箱内附魔金苹果数量（搜索掷骰的结果）；0 表示未知或没有。 */
    public final int goldCount;

    public FoundChest(BlockPos pos, double distance, String label) {
        this(pos, distance, label, 0);
    }

    public FoundChest(BlockPos pos, double distance, String label, int goldCount) {
        this.pos = pos;
        this.distance = distance;
        this.label = label;
        this.goldCount = Math.max(0, goldCount);
    }
}
