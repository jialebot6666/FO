package com.fo.addon.elytra.core;

import com.fo.addon.elytra.core.FOElytraLog;
import com.fo.addon.elytra.core.InvHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

public final class BlockBreaker {
    private static final int MAX_TICKS_PER_BLOCK = 200;
    private static BlockPos target;
    private static int ticks;
    private static boolean breaking;

    private BlockBreaker() {
    }

    public static void reset() {
        target = null;
        ticks = 0;
        breaking = false;
    }

    public static boolean isBreaking() {
        return breaking;
    }

    public static boolean tick(BlockPos pos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            return false;
        }
        if (mc.world.getBlockState(pos).isAir()) {
            BlockBreaker.reset();
            return true;
        }
        if (!pos.equals((Object)target)) {
            target = pos;
            ticks = 0;
        }
        if (ticks++ > 200) {
            FOElytraLog.warn("\u6316\u65b9\u5757\u8d85\u65f6\uff08%d, %d, %d\uff09\u2014\u2014\u53ef\u80fd\u6ca1\u6709\u53ef\u7528\u7684\u9550", pos.getX(), pos.getY(), pos.getZ());
            BlockBreaker.cancel();
            return mc.world.getBlockState(pos).isAir();
        }
        int pick = BlockBreaker.findBestPickaxe();
        if (pick >= 0 && mc.player.getInventory().getSelectedSlot() != pick) {
            InvHelper.selectSlot(pick);
        }
        InvHelper.lookAt((PlayerEntity)mc.player, Vec3d.ofCenter((Vec3i)pos));
        Direction side = BlockBreaker.bestSide(mc, pos);
        if (mc.interactionManager.isBreakingBlock()) {
            mc.interactionManager.updateBlockBreakingProgress(pos, side);
        } else {
            mc.interactionManager.attackBlock(pos, side);
        }
        breaking = true;
        mc.player.swingHand(Hand.MAIN_HAND);
        return false;
    }

    public static void cancel() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.interactionManager != null && mc.interactionManager.isBreakingBlock()) {
            mc.interactionManager.cancelBlockBreaking();
        }
        BlockBreaker.reset();
    }

    private static Direction bestSide(MinecraftClient mc, BlockPos pos) {
        return Direction.getFacing((double)((double)pos.getX() + 0.5 - mc.player.getX()), (double)((double)pos.getY() + 0.5 - mc.player.getEyeY()), (double)((double)pos.getZ() + 0.5 - mc.player.getZ()));
    }

    public static int findBestPickaxe() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return -1;
        }
        int best = -1;
        int bestScore = -1;
        for (int i = 0; i < 9; ++i) {
            int score;
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty() || (score = BlockBreaker.pickaxeScore(s)) <= bestScore) continue;
            bestScore = score;
            best = i;
        }
        return bestScore <= 0 ? -1 : best;
    }

    private static int pickaxeScore(ItemStack stack) {
        if (stack.isOf(Items.NETHERITE_PICKAXE)) {
            return 100;
        }
        if (stack.isOf(Items.DIAMOND_PICKAXE)) {
            return 90;
        }
        if (stack.isOf(Items.IRON_PICKAXE)) {
            return 70;
        }
        if (stack.isOf(Items.STONE_PICKAXE)) {
            return 50;
        }
        if (stack.isOf(Items.GOLDEN_PICKAXE)) {
            return 40;
        }
        if (stack.isOf(Items.WOODEN_PICKAXE)) {
            return 30;
        }
        return 0;
    }
}

