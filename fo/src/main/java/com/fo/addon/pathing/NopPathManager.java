package com.fo.addon.pathing;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.function.Predicate;

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
    public void pickupItems(Predicate<ItemStack> filter) {
    }

    @Override
    public void protectShulkerBoxes(boolean protect) {
    }
}