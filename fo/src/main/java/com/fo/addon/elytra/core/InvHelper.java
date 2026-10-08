package com.fo.addon.elytra.core;

import com.fo.addon.elytra.core.FOElytraLog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Predicate;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.BlockView;

public final class InvHelper {
    private InvHelper() {
    }

    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    public static int playerSlotId(ScreenHandler handler, PlayerEntity player, int invIndex) {
        if (handler == null || player == null) {
            return -1;
        }
        OptionalInt id = handler.getSlotIndex((Inventory)player.getInventory(), invIndex);
        return id.isPresent() ? id.getAsInt() : -1;
    }

    public static int currentPlayerSlotId(int invIndex) {
        MinecraftClient mc = InvHelper.mc();
        if (mc.player == null) {
            return -1;
        }
        return InvHelper.playerSlotId(mc.player.currentScreenHandler, (PlayerEntity)mc.player, invIndex);
    }

    public static void click(ScreenHandler handler, int slotId, int button, SlotActionType type) {
        MinecraftClient mc = InvHelper.mc();
        if (handler == null || mc.player == null || mc.interactionManager == null) {
            return;
        }
        if (slotId < 0 || slotId >= handler.slots.size()) {
            return;
        }
        try {
            if (FOElytraLog.fileVerbose) {
                ItemStack s = ((Slot)handler.slots.get(slotId)).getStack();
                FOElytraLog.detail("\u70b9\u51fb slot=%d(%-14s x%d) button=%d type=%s syncId=%d", slotId, s.isEmpty() ? "\u7a7a" : s.getItem().getName().getString(), s.getCount(), button, type, handler.syncId);
            }
            mc.interactionManager.clickSlot(handler.syncId, slotId, button, type, (PlayerEntity)mc.player);
        }
        catch (Throwable t) {
            FOElytraLog.debug("clickSlot \u5931\u8d25 slot=%d: %s", slotId, t);
            FOElytraLog.detailError("clickSlot slot=" + slotId, t);
        }
    }

    public static boolean dropSlot(ScreenHandler handler, int slotId) {
        MinecraftClient mc = InvHelper.mc();
        if (handler == null || mc.player == null || mc.interactionManager == null) {
            return false;
        }
        if (slotId < 0 || slotId >= handler.slots.size()) {
            return false;
        }
        try {
            mc.interactionManager.clickSlot(handler.syncId, slotId, 0, SlotActionType.PICKUP, (PlayerEntity)mc.player);
            mc.interactionManager.clickSlot(handler.syncId, -999, 0, SlotActionType.PICKUP, (PlayerEntity)mc.player);
            if (handler.getCursorStack().isEmpty()) {
                return true;
            }
            FOElytraLog.warn("\u4e22\u7269\u54c1\uff1a\u670d\u52a1\u7aef\u6ca1\u628a\u5149\u6807\u4e0a\u7684 %s x%d \u6254\u6389\uff0c\u5148\u653e\u56de\u539f\u69fd\u4f4d\uff08\u4e0d\u505a\u522b\u7684\u52a8\u4f5c\uff09", handler.getCursorStack().getName().getString(), handler.getCursorStack().getCount());
            mc.interactionManager.clickSlot(handler.syncId, slotId, 0, SlotActionType.PICKUP, (PlayerEntity)mc.player);
            return false;
        }
        catch (Throwable t) {
            FOElytraLog.debug("dropSlot \u5931\u8d25 slot=%d: %s", slotId, t);
            FOElytraLog.detailError("dropSlot slot=" + slotId, t);
            return false;
        }
    }

    public static void pickup(ScreenHandler handler, int slotId) {
        InvHelper.click(handler, slotId, 0, SlotActionType.PICKUP);
    }

    public static void pickupHalf(ScreenHandler handler, int slotId) {
        InvHelper.click(handler, slotId, 1, SlotActionType.PICKUP);
    }

    public static void quickMove(ScreenHandler handler, int slotId) {
        InvHelper.click(handler, slotId, 0, SlotActionType.QUICK_MOVE);
    }

    public static void pickupAll(ScreenHandler handler, int slotId) {
        InvHelper.click(handler, slotId, 0, SlotActionType.PICKUP_ALL);
    }

    public static void moveStack(ScreenHandler handler, int fromSlot, int toSlot) {
        InvHelper.pickup(handler, fromSlot);
        InvHelper.pickup(handler, toSlot);
        InvHelper.pickup(handler, fromSlot);
    }

    public static void swapWithHotbar(ScreenHandler handler, int slotId, int hotbarIndex) {
        InvHelper.click(handler, slotId, hotbarIndex, SlotActionType.SWAP);
    }

    public static void clickPlayerInv(int invIndex, int button, SlotActionType type) {
        MinecraftClient mc = InvHelper.mc();
        if (mc.player == null) {
            return;
        }
        if (mc.currentScreen != null) {
            FOElytraLog.debug("\u73a9\u5bb6\u6b63\u5f00\u7740\u754c\u9762\uff0c\u8df3\u8fc7\u80cc\u5305\u70b9\u51fb\uff08\u907f\u514d\u62a2\u9f20\u6807\u64cd\u4f5c\uff09", new Object[0]);
            return;
        }
        ScreenHandler handler = mc.player.currentScreenHandler;
        int raw = InvHelper.playerSlotId(handler, (PlayerEntity)mc.player, invIndex);
        InvHelper.click(handler, raw, button, type);
    }

    public static void moveInvToHotbar(int invIndex, int hotbarSlot) {
        MinecraftClient mc = InvHelper.mc();
        if (mc.player == null || hotbarSlot < 0 || hotbarSlot > 8) {
            return;
        }
        if (mc.currentScreen != null) {
            FOElytraLog.debug("\u73a9\u5bb6\u6b63\u5f00\u7740\u754c\u9762\uff0c\u8df3\u8fc7\u5feb\u6377\u680f\u6574\u7406", new Object[0]);
            return;
        }
        ScreenHandler handler = mc.player.currentScreenHandler;
        int raw = InvHelper.playerSlotId(handler, (PlayerEntity)mc.player, invIndex);
        InvHelper.click(handler, raw, hotbarSlot, SlotActionType.SWAP);
    }

    public static int findSlot(Predicate<ItemStack> predicate) {
        return InvHelper.findSlot(predicate, 0, 36);
    }

    public static int findSlot(Predicate<ItemStack> predicate, int from, int to) {
        MinecraftClient mc = InvHelper.mc();
        if (mc.player == null) {
            return -1;
        }
        for (int i = from; i < to; ++i) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty() || !predicate.test(s)) continue;
            return i;
        }
        return -1;
    }

    public static int findEmptyHotbarSlot() {
        return InvHelper.findSlot(ItemStack::isEmpty, 0, 9);
    }

    public static int emptyBackpackSlots() {
        return (int)InvHelper.countSlots(ItemStack::isEmpty, 9, 36);
    }

    private static long countSlots(Predicate<ItemStack> predicate, int from, int to) {
        MinecraftClient mc = InvHelper.mc();
        if (mc.player == null) {
            return 0L;
        }
        long n = 0L;
        for (int i = from; i < to; ++i) {
            if (!predicate.test(mc.player.getInventory().getStack(i))) continue;
            ++n;
        }
        return n;
    }

    public static void mergeSameItems(ScreenHandler handler, Predicate<ItemStack> predicate, int fromTo, int to) {
        MinecraftClient mc = InvHelper.mc();
        if (handler == null || mc.player == null) {
            return;
        }
        for (int guard = 0; guard < 64; ++guard) {
            ArrayList<Integer> slots = new ArrayList<Integer>();
            for (int i2 = fromTo; i2 < to; ++i2) {
                ItemStack s;
                if (i2 < 0 || i2 >= handler.slots.size() || (s = ((Slot)handler.slots.get(i2)).getStack()).isEmpty() || !predicate.test(s)) continue;
                slots.add(i2);
            }
            if (slots.size() < 2) {
                return;
            }
            slots.sort(Comparator.comparingInt(i -> ((Slot)handler.slots.get(i.intValue())).getStack().getCount()));
            int first = (Integer)slots.get(0);
            ItemStack secondStack = ((Slot)handler.slots.get(((Integer)slots.get(1)).intValue())).getStack();
            if (secondStack.getCount() >= secondStack.getMaxCount()) {
                return;
            }
            InvHelper.pickup(handler, first);
            InvHelper.pickupAll(handler, first);
            InvHelper.pickup(handler, first);
        }
    }

    public static void lookAt(PlayerEntity player, Vec3d target) {
        if (player == null) {
            return;
        }
        Vec3d eyes = player.getEyePos();
        Vec3d dir = target.subtract(eyes);
        double distXZ = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float yaw = (float)(Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90.0);
        float pitch = (float)(-Math.toDegrees(Math.atan2(dir.y, distXZ)));
        player.setYaw(yaw);
        player.setPitch(pitch);
    }

    public static BlockPos findPlaceTarget(PlayerEntity player, int radius) {
        List<BlockPos> list = InvHelper.findPlaceTargets(player, radius, false);
        return list.isEmpty() ? null : list.get(0);
    }

    public static List<BlockPos> findPlaceTargets(PlayerEntity player, int radius) {
        return InvHelper.findPlaceTargets(player, radius, false);
    }

    public static List<BlockPos> findPlaceTargets(PlayerEntity player, int radius, boolean ignorePlayerBox) {
        MinecraftClient mc = InvHelper.mc();
        ArrayList<BlockPos> out = new ArrayList<BlockPos>();
        if (player == null || mc.world == null) {
            return out;
        }
        BlockPos origin = player.getBlockPos();
        for (int dy = 0; dy >= -radius; --dy) {
            for (int r = 1; r <= radius; ++r) {
                for (int dx = -r; dx <= r; ++dx) {
                    for (int dz = -r; dz <= r; ++dz) {
                        BlockPos target;
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r || !InvHelper.isPlaceable(mc, target = origin.add(dx, dy, dz)) || !ignorePlayerBox && InvHelper.blockedByPlayer(target)) continue;
                        out.add(target);
                    }
                }
            }
        }
        return out;
    }

    public static boolean blockedByPlayer(BlockPos pos) {
        MinecraftClient mc = InvHelper.mc();
        if (pos == null || mc.player == null) {
            return false;
        }
        return new Box(pos).intersects(mc.player.getBoundingBox());
    }

    private static boolean isPlaceable(MinecraftClient mc, BlockPos target) {
        BlockState state = mc.world.getBlockState(target);
        if (!state.isAir() && !state.isReplaceable()) {
            return false;
        }
        BlockPos below = target.down();
        BlockState belowState = mc.world.getBlockState(below);
        if (!belowState.isSolidBlock((BlockView)mc.world, below)) {
            return false;
        }
        if (belowState.getBlock() == Blocks.MAGMA_BLOCK) {
            return false;
        }
        if (belowState.getBlock() == Blocks.LAVA) {
            return false;
        }
        BlockPos above = target.up();
        return mc.world.getBlockState(above).isAir();
    }

    public static void selectSlot(int slot) {
        MinecraftClient mc = InvHelper.mc();
        if (mc.player == null || slot < 0 || slot > 8) {
            return;
        }
        mc.player.getInventory().setSelectedSlot(slot);
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket((Packet)new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    public static boolean placeBlock(BlockPos target, int hotbarSlot) {
        MinecraftClient mc = InvHelper.mc();
        if (mc.player == null || mc.interactionManager == null) {
            return false;
        }
        if (hotbarSlot < 0 || hotbarSlot > 8) {
            return false;
        }
        InvHelper.selectSlot(hotbarSlot);
        InvHelper.lookAt((PlayerEntity)mc.player, Vec3d.ofCenter((Vec3i)target));
        BlockPos support = target.down();
        Vec3d hitPos = Vec3d.ofCenter((Vec3i)support).add(0.0, 0.5, 0.0);
        BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, support, false);
        mc.player.swingHand(Hand.MAIN_HAND);
        return mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit).isAccepted();
    }

    public static boolean interactBlock(BlockPos pos) {
        MinecraftClient mc = InvHelper.mc();
        if (mc.player == null || mc.interactionManager == null || mc.world == null) {
            return false;
        }
        InvHelper.lookAt((PlayerEntity)mc.player, Vec3d.ofCenter((Vec3i)pos));
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter((Vec3i)pos), Direction.UP, pos, false);
        mc.player.swingHand(Hand.MAIN_HAND);
        return mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit).isAccepted();
    }

    public static boolean useItem(Hand hand) {
        MinecraftClient mc = InvHelper.mc();
        if (mc.player == null || mc.interactionManager == null) {
            return false;
        }
        return mc.interactionManager.interactItem((PlayerEntity)mc.player, hand).isAccepted();
    }

    public static boolean heldItemIsBlock(int hotbarSlot) {
        MinecraftClient mc = InvHelper.mc();
        if (mc.player == null || hotbarSlot < 0 || hotbarSlot > 8) {
            return false;
        }
        ItemStack s = mc.player.getInventory().getStack(hotbarSlot);
        return !s.isEmpty() && s.getItem() instanceof BlockItem;
    }

    public static HandledScreen<?> currentContainerScreen(String titleKeyword) {
        MinecraftClient mc = InvHelper.mc();
        Screen screen2 = mc.currentScreen;
        if (!(screen2 instanceof HandledScreen)) {
            return null;
        }
        HandledScreen handled = (HandledScreen)screen2;
        String title = handled.getTitle().getString();
        if (titleKeyword != null && !titleKeyword.isEmpty() && !title.equalsIgnoreCase(titleKeyword)) {
            return null;
        }
        return handled;
    }

    public static boolean hasContainerOpen() {
        MinecraftClient mc = InvHelper.mc();
        if (mc.player == null) {
            return false;
        }
        return mc.currentScreen instanceof HandledScreen && mc.player.currentScreenHandler != mc.player.playerScreenHandler;
    }

    public static boolean screenOpen() {
        return InvHelper.mc().currentScreen != null;
    }

    public static void closeScreen() {
        MinecraftClient mc = InvHelper.mc();
        if (mc.player != null && mc.currentScreen != null) {
            mc.player.closeScreen();
        }
    }
}

