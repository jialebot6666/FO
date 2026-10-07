package com.fo.addon.elytra.core;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Predicate;
public final class InvHelper {
    private InvHelper() {
    }
    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }
    public static int playerSlotId(ScreenHandler handler, PlayerEntity player, int invIndex) {
        if (handler == null || player == null) return -1;
        OptionalInt id = handler.getSlotIndex(player.getInventory(), invIndex);
        return id.isPresent() ? id.getAsInt() : -1;
    }
    public static int currentPlayerSlotId(int invIndex) {
        MinecraftClient mc = mc();
        if (mc.player == null) return -1;
        return playerSlotId(mc.player.currentScreenHandler, mc.player, invIndex);
    }
    public static void click(ScreenHandler handler, int slotId, int button, SlotActionType type) {
        MinecraftClient mc = mc();
        if (handler == null || mc.player == null || mc.interactionManager == null) return;
        if (slotId < 0 || slotId >= handler.slots.size()) return;
        try {
            if (FOElytraLog.fileVerbose) {
                ItemStack s = handler.slots.get(slotId).getStack();
                FOElytraLog.detail("点击 slot=%d(%-14s x%d) button=%d type=%s syncId=%d",
                    slotId, s.isEmpty() ? "空" : s.getItem().getName().getString(), s.getCount(),
                    button, type, handler.syncId);
            }
            mc.interactionManager.clickSlot(handler.syncId, slotId, button, type, mc.player);
        } catch (Throwable t) {
            FOElytraLog.debug("clickSlot 失败 slot=%d: %s", slotId, t);
            FOElytraLog.detailError("clickSlot slot=" + slotId, t);
        }
    }
    public static void pickup(ScreenHandler handler, int slotId) {
        click(handler, slotId, 0, SlotActionType.PICKUP);
    }
    public static void pickupHalf(ScreenHandler handler, int slotId) {
        click(handler, slotId, 1, SlotActionType.PICKUP);
    }
    public static void quickMove(ScreenHandler handler, int slotId) {
        click(handler, slotId, 0, SlotActionType.QUICK_MOVE);
    }
    public static void moveStack(ScreenHandler handler, int fromSlot, int toSlot) {
        pickup(handler, fromSlot);
        pickup(handler, toSlot);
        pickup(handler, fromSlot);
    }
    public static void swapWithHotbar(ScreenHandler handler, int slotId, int hotbarIndex) {
        click(handler, slotId, hotbarIndex, SlotActionType.SWAP);
    }
    public static void clickPlayerInv(int invIndex, int button, SlotActionType type) {
        MinecraftClient mc = mc();
        if (mc.player == null) return;
        if (mc.currentScreen != null) {
            FOElytraLog.debug("玩家正开着界面，跳过背包点击（避免抢鼠标操作）");
            return;
        }
        ScreenHandler handler = mc.player.currentScreenHandler;
        int raw = playerSlotId(handler, mc.player, invIndex);
        click(handler, raw, button, type);
    }
    public static void moveInvToHotbar(int invIndex, int hotbarSlot) {
        MinecraftClient mc = mc();
        if (mc.player == null || hotbarSlot < 0 || hotbarSlot > 8) return;
        if (mc.currentScreen != null) {
            FOElytraLog.debug("玩家正开着界面，跳过快捷栏整理");
            return;
        }
        ScreenHandler handler = mc.player.currentScreenHandler;
        int raw = playerSlotId(handler, mc.player, invIndex);
        click(handler, raw, hotbarSlot, SlotActionType.SWAP);
    }
    public static int findSlot(Predicate<ItemStack> predicate) {
        return findSlot(predicate, 0, 36);
    }
    public static int findSlot(Predicate<ItemStack> predicate, int from, int to) {
        MinecraftClient mc = mc();
        if (mc.player == null) return -1;
        for (int i = from; i < to; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && predicate.test(s)) return i;
        }
        return -1;
    }
    public static int findEmptyHotbarSlot() {
        return findSlot(ItemStack::isEmpty, 0, 9);
    }
    public static int emptyBackpackSlots() {
        return (int) countSlots(ItemStack::isEmpty, 9, 36);
    }
    private static long countSlots(Predicate<ItemStack> predicate, int from, int to) {
        MinecraftClient mc = mc();
        if (mc.player == null) return 0;
        long n = 0;
        for (int i = from; i < to; i++) {
            if (predicate.test(mc.player.getInventory().getStack(i))) n++;
        }
        return n;
    }
    public static void mergeSameItems(ScreenHandler handler, Predicate<ItemStack> predicate, int fromTo, int to) {
        MinecraftClient mc = mc();
        if (handler == null || mc.player == null) return;
        for (int guard = 0; guard < 64; guard++) {
            List<Integer> slots = new ArrayList<>();
            for (int i = fromTo; i < to; i++) {
                if (i < 0 || i >= handler.slots.size()) continue;
                ItemStack s = handler.slots.get(i).getStack();
                if (!s.isEmpty() && predicate.test(s)) slots.add(i);
            }
            if (slots.size() < 2) return;
            slots.sort(Comparator.comparingInt(i -> handler.slots.get(i).getStack().getCount()));
            int first = slots.get(0);
            int second = slots.get(1);
            ItemStack secondStack = handler.slots.get(second).getStack();
            if (secondStack.getCount() >= secondStack.getMaxCount()) return;
            pickup(handler, first);
            pickup(handler, second);
            pickup(handler, first);
        }
    }
    public static void lookAt(PlayerEntity player, Vec3d target) {
        if (player == null) return;
        Vec3d eyes = player.getEyePos();
        Vec3d dir = target.subtract(eyes);
        double distXZ = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dir.y, distXZ)));
        player.setYaw(yaw);
        player.setPitch(pitch);
    }
    public static BlockPos findPlaceTarget(PlayerEntity player, int radius) {
        List<BlockPos> list = findPlaceTargets(player, radius, false);
        return list.isEmpty() ? null : list.get(0);
    }

    public static List<BlockPos> findPlaceTargets(PlayerEntity player, int radius) {
        return findPlaceTargets(player, radius, false);
    }

    public static List<BlockPos> findPlaceTargets(PlayerEntity player, int radius, boolean ignorePlayerBox) {
        MinecraftClient mc = mc();
        List<BlockPos> out = new ArrayList<>();
        if (player == null || mc.world == null) return out;
        BlockPos origin = player.getBlockPos();
        for (int dy = 0; dy >= -radius; dy--) {
            for (int r = 1; r <= radius; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        BlockPos target = origin.add(dx, dy, dz);
                        if (!isPlaceable(mc, target)) continue;
                        if (!ignorePlayerBox && blockedByPlayer(target)) continue;
                        out.add(target);
                    }
                }
            }
        }
        return out;
    }

    public static boolean blockedByPlayer(BlockPos pos) {
        MinecraftClient mc = mc();
        if (pos == null || mc.player == null) return false;
        return new Box(pos).intersects(mc.player.getBoundingBox());
    }
    private static boolean isPlaceable(MinecraftClient mc, BlockPos target) {
        BlockState state = mc.world.getBlockState(target);
        if (!state.isAir() && !state.isReplaceable()) return false;
        BlockPos below = target.down();
        BlockState belowState = mc.world.getBlockState(below);
        if (!belowState.isSolidBlock(mc.world, below)) return false;
        if (belowState.getBlock() == net.minecraft.block.Blocks.MAGMA_BLOCK) return false;
        if (belowState.getBlock() == net.minecraft.block.Blocks.LAVA) return false;
        BlockPos above = target.up();
        return mc.world.getBlockState(above).isAir();
    }
    public static boolean placeBlock(BlockPos target, int hotbarSlot) {
        MinecraftClient mc = mc();
        if (mc.player == null || mc.interactionManager == null) return false;
        if (hotbarSlot < 0 || hotbarSlot > 8) return false;
        mc.player.getInventory().setSelectedSlot(hotbarSlot);
        lookAt(mc.player, Vec3d.ofCenter(target));
        BlockPos support = target.down();
        Vec3d hitPos = Vec3d.ofCenter(support).add(0, 0.5, 0);
        BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, support, false);
        mc.player.swingHand(Hand.MAIN_HAND);
        return mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit).isAccepted();
    }
    public static boolean interactBlock(BlockPos pos) {
        MinecraftClient mc = mc();
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return false;
        lookAt(mc.player, Vec3d.ofCenter(pos));
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        mc.player.swingHand(Hand.MAIN_HAND);
        return mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit).isAccepted();
    }
    public static boolean useItem(Hand hand) {
        MinecraftClient mc = mc();
        if (mc.player == null || mc.interactionManager == null) return false;
        return mc.interactionManager.interactItem(mc.player, hand).isAccepted();
    }
    public static boolean heldItemIsBlock(int hotbarSlot) {
        MinecraftClient mc = mc();
        if (mc.player == null || hotbarSlot < 0 || hotbarSlot > 8) return false;
        ItemStack s = mc.player.getInventory().getStack(hotbarSlot);
        return !s.isEmpty() && s.getItem() instanceof BlockItem;
    }
    public static net.minecraft.client.gui.screen.ingame.HandledScreen<?> currentContainerScreen(String titleKeyword) {
        MinecraftClient mc = mc();
        if (!(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?> handled)) return null;
        String title = handled.getTitle().getString();
        if (titleKeyword != null && !titleKeyword.isEmpty() && !title.equalsIgnoreCase(titleKeyword)) return null;
        return handled;
    }
    public static boolean hasContainerOpen() {
        MinecraftClient mc = mc();
        if (mc.player == null) return false;
        return mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?>
            && mc.player.currentScreenHandler != mc.player.playerScreenHandler;
    }
    public static boolean screenOpen() {
        return mc().currentScreen != null;
    }
    public static void closeScreen() {
        MinecraftClient mc = mc();
        if (mc.player != null && mc.currentScreen != null) mc.player.closeScreen();
    }
}
