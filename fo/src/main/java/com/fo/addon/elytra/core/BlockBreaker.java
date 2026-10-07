package com.fo.addon.elytra.core;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
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
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return false;
        if (mc.world.getBlockState(pos).isAir()) {
            reset();
            return true;
        }
        if (!pos.equals(target)) {
            target = pos;
            ticks = 0;
        }
        if (ticks++ > MAX_TICKS_PER_BLOCK) {
            FOElytraLog.warn("挖方块超时（%d, %d, %d）——可能没有可用的镐",
                pos.getX(), pos.getY(), pos.getZ());
            cancel();
            return mc.world.getBlockState(pos).isAir();
        }
        int pick = findBestPickaxe();
        if (pick >= 0 && mc.player.getInventory().getSelectedSlot() != pick) {
            mc.player.getInventory().setSelectedSlot(pick);
        }
        InvHelper.lookAt(mc.player, net.minecraft.util.math.Vec3d.ofCenter(pos));
        Direction side = bestSide(mc, pos);
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
        reset();
    }
    private static Direction bestSide(MinecraftClient mc, BlockPos pos) {
        return Direction.getFacing(
            pos.getX() + 0.5 - mc.player.getX(),
            pos.getY() + 0.5 - mc.player.getEyeY(),
            pos.getZ() + 0.5 - mc.player.getZ()
        );
    }
    public static int findBestPickaxe() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return -1;
        int best = -1;
        int bestScore = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            int score = pickaxeScore(s);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return bestScore <= 0 ? -1 : best;
    }
    private static int pickaxeScore(ItemStack stack) {
        if (stack.isOf(Items.NETHERITE_PICKAXE)) return 100;
        if (stack.isOf(Items.DIAMOND_PICKAXE)) return 90;
        if (stack.isOf(Items.IRON_PICKAXE)) return 70;
        if (stack.isOf(Items.STONE_PICKAXE)) return 50;
        if (stack.isOf(Items.GOLDEN_PICKAXE)) return 40;
        if (stack.isOf(Items.WOODEN_PICKAXE)) return 30;
        return 0;
    }
}
