package com.fo.addon.elytra.core;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
public final class InventoryRestocker {
    private int cooldown;
    private int cursor;
    public void reset() {
        cooldown = 0;
        cursor = 0;
    }
    public boolean tick(List<Item> items, int stacksEach, boolean keepHeld, int interval) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;
        if (items == null || items.isEmpty()) return false;
        if (InvHelper.screenOpen()) return false;
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        int held = mc.player.getInventory().getSelectedSlot();
        int size = items.size();
        for (int n = 0; n < size; n++) {
            Item item = items.get((cursor + n) % size);
            if (item == null) continue;
            int target = Math.max(1, stacksEach) * ItemHelper.stackSize(item);
            if (ItemHelper.countInHotbar(mc.player, item) >= target) continue;
            int source = fullestBackpackSlot(mc, item);
            if (source < 0) continue;
            int dest = pickDestination(mc, items, keepHeld, held);
            if (dest < 0) continue;
            InvHelper.moveInvToHotbar(source, dest);
            cursor = (cursor + n + 1) % size;
            cooldown = Math.max(1, interval);
            FOElytraLog.debug("物品栏补充：%s 背包 #%d → 快捷栏 #%d", item.getName().getString(), source, dest);
            FOElytraLog.detail("快捷栏自动补充：%s 槽位 %d → %d", item.getName().getString(), source, dest);
            return true;
        }
        return false;
    }
    private static int fullestBackpackSlot(MinecraftClient mc, Item item) {
        int best = -1;
        int bestCount = 0;
        for (int i = 9; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty() || !s.isOf(item)) continue;
            if (s.getCount() > bestCount) {
                bestCount = s.getCount();
                best = i;
            }
        }
        return best;
    }
    private static int pickDestination(MinecraftClient mc, List<Item> items, boolean keepHeld, int held) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (keepHeld && i == held) continue;
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) return i;
            if (items.contains(s.getItem())) continue;
            if (isProtected(s)) continue;
            return i;
        }
        return -1;
    }
    private static boolean isProtected(ItemStack s) {
        return s.isOf(net.minecraft.item.Items.FIREWORK_ROCKET)
            || s.isOf(net.minecraft.item.Items.EXPERIENCE_BOTTLE)
            || s.isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING)
            || s.isOf(net.minecraft.item.Items.ELYTRA)
            || s.isOf(net.minecraft.item.Items.ENDER_CHEST)
            || ItemHelper.isShulkerBox(s)
            || ItemHelper.isFood(s);
    }
    public static String shortage(List<Item> items, int stacksEach) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || items == null) return null;
        for (Item item : items) {
            if (item == null) continue;
            if (!isShort(mc.player, item, stacksEach)) continue;
            int target = Math.max(1, stacksEach) * ItemHelper.stackSize(item);
            int have = ItemHelper.countInInventory(mc.player, item);
            return String.format("%s 只有 %d/%d", item.getName().getString(), have, target);
        }
        return null;
    }
    public static boolean isShort(net.minecraft.entity.player.PlayerEntity player, Item item, int stacksEach) {
        if (player == null || item == null) return false;
        int target = Math.max(1, stacksEach) * ItemHelper.stackSize(item);
        return ItemHelper.countInInventory(player, item) * 2 < target;
    }
}
