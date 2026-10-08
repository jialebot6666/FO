package com.fo.addon.elytra.core;

import com.fo.addon.elytra.core.FOElytraLog;
import com.fo.addon.elytra.core.InvHelper;
import com.fo.addon.elytra.core.ItemHelper;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class InventoryRestocker {
    private int cooldown;
    private int cursor;

    public void reset() {
        this.cooldown = 0;
        this.cursor = 0;
    }

    public boolean tick(List<Item> items, int stacksEach, boolean keepHeld, int interval) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return false;
        }
        if (items == null || items.isEmpty()) {
            return false;
        }
        if (InvHelper.screenOpen()) {
            return false;
        }
        if (this.cooldown > 0) {
            --this.cooldown;
            return false;
        }
        int held = mc.player.getInventory().getSelectedSlot();
        int size = items.size();
        for (int n = 0; n < size; ++n) {
            int dest;
            int source;
            Item item = items.get((this.cursor + n) % size);
            if (item == null) continue;
            int target = Math.max(1, stacksEach) * ItemHelper.stackSize(item);
            if (ItemHelper.countInHotbar((PlayerEntity)mc.player, item) >= target || (source = InventoryRestocker.fullestBackpackSlot(mc, item)) < 0 || (dest = InventoryRestocker.pickDestination(mc, items, keepHeld, held)) < 0) continue;
            InvHelper.moveInvToHotbar(source, dest);
            this.cursor = (this.cursor + n + 1) % size;
            this.cooldown = Math.max(1, interval);
            FOElytraLog.debug("\u7269\u54c1\u680f\u8865\u5145\uff1a%s \u80cc\u5305 #%d \u2192 \u5feb\u6377\u680f #%d", item.getName().getString(), source, dest);
            FOElytraLog.detail("\u5feb\u6377\u680f\u81ea\u52a8\u8865\u5145\uff1a%s \u69fd\u4f4d %d \u2192 %d", item.getName().getString(), source, dest);
            return true;
        }
        return false;
    }

    private static int fullestBackpackSlot(MinecraftClient mc, Item item) {
        int best = -1;
        int bestCount = 0;
        for (int i = 9; i < 36; ++i) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty() || !s.isOf(item) || s.getCount() <= bestCount) continue;
            bestCount = s.getCount();
            best = i;
        }
        return best;
    }

    private static int pickDestination(MinecraftClient mc, List<Item> items, boolean keepHeld, int held) {
        int i;
        for (i = 0; i < 9; ++i) {
            if (!mc.player.getInventory().getStack(i).isEmpty()) continue;
            return i;
        }
        for (i = 0; i < 9; ++i) {
            if (keepHeld && i == held) continue;
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) {
                return i;
            }
            if (items.contains(s.getItem()) || InventoryRestocker.isProtected(s)) continue;
            return i;
        }
        return -1;
    }

    private static boolean isProtected(ItemStack s) {
        return s.isOf(Items.FIREWORK_ROCKET) || s.isOf(Items.EXPERIENCE_BOTTLE) || s.isOf(Items.TOTEM_OF_UNDYING) || s.isOf(Items.ELYTRA) || s.isOf(Items.ENDER_CHEST) || ItemHelper.isShulkerBox(s) || ItemHelper.isFood(s);
    }

    public static String shortage(List<Item> items, int stacksEach) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || items == null) {
            return null;
        }
        for (Item item : items) {
            if (item == null || !InventoryRestocker.isShort((PlayerEntity)mc.player, item, stacksEach)) continue;
            int target = Math.max(1, stacksEach) * ItemHelper.stackSize(item);
            int have = ItemHelper.countInInventory((PlayerEntity)mc.player, item);
            return String.format("%s \u53ea\u6709 %d/%d", item.getName().getString(), have, target);
        }
        return null;
    }

    public static boolean isShort(PlayerEntity player, Item item, int stacksEach) {
        if (player == null || item == null) {
            return false;
        }
        int target = Math.max(1, stacksEach) * ItemHelper.stackSize(item);
        return ItemHelper.countInInventory(player, item) * 2 < target;
    }
}

