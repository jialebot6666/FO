package com.fo.addon.pathing;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.function.Predicate;

public interface IPathManager {
    boolean isPathing();

    void stop();

    void moveTo(BlockPos pos, boolean ignoreY);

    void mine(Block... blocks);

    /** 让 Baritone 持续走到匹配的物品掉落物旁拾取（FollowProcess.pickup） */
    void pickupItems(Predicate<ItemStack> filter);

    void protectShulkerBoxes(boolean protect);
}