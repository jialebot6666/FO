package com.fo.addon.ancient.core;

import net.minecraft.util.math.BlockPos;

/** 古城中一个符合条件的战利品箱子（移植自 misaka 古城模块）。 */
public final class FoundChest {
    public final BlockPos pos;
    public final double distance;
    public final String label;

    public FoundChest(BlockPos pos, double distance, String label) {
        this.pos = pos;
        this.distance = distance;
        this.label = label;
    }
}
