package com.fo.addon.pathing;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

public interface IPathManager {
    boolean isPathing();

    void stop();

    void moveTo(BlockPos pos, boolean ignoreY);

    void mine(Block... blocks);

    void protectShulkerBoxes(boolean protect);
}