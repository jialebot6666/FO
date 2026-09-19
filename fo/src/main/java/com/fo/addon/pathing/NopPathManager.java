package com.fo.addon.pathing;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

public class NopPathManager implements IPathManager {
    @Override
    public boolean isPathing() {
        return false;
    }

    @Override
    public void stop() {
    }

    @Override
    public void moveTo(BlockPos pos, boolean ignoreY) {
    }

    @Override
    public void mine(Block... blocks) {
    }

    @Override
    public void protectShulkerBoxes(boolean protect) {
    }
}