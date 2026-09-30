package com.fo.addon.pathing;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.function.Predicate;

public interface IPathManager {
    boolean isPathing();

    void stop();

    void moveTo(BlockPos pos, boolean ignoreY);

    /**
     * 精确寻路到目标格内（V4.19）：GoalGetToBlock 只走到目标格相邻格就停（玩家中心
     * 进不了目标格，站盒子旁会留 2-3 格空隙），这里改用 GoalNear(pos, 0.5)——
     * 玩家中心进入目标格 0.5 格内才算到达，真正"一格空间都不留"。
     * 默认退化为 moveTo(pos, false)，由 BaritonePathManager 覆盖为精确目标。
     */
    default void moveToPrecise(BlockPos pos) {
        moveTo(pos, false);
    }

    void mine(Block... blocks);

    /** 让 Baritone 持续走到匹配的物品掉落物旁拾取（FollowProcess.pickup） */
    void pickupItems(Predicate<ItemStack> filter);

    void protectShulkerBoxes(boolean protect);
}