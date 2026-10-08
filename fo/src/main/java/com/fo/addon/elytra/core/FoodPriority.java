package com.fo.addon.elytra.core;

import com.fo.addon.elytra.core.FOElytraLog;
import com.fo.addon.elytra.core.InvHelper;
import com.fo.addon.elytra.core.ItemHelper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class FoodPriority {
    private FoodPriority() {
    }

    public static List<Item> parse(String csv) {
        ArrayList<Item> out = new ArrayList<Item>();
        ArrayList<String> bad = new ArrayList<String>();
        if (csv == null || csv.isBlank()) {
            return out;
        }
        try {
            for (String raw : csv.split("[,;\\s]+")) {
                String s;
                if (raw == null || (s = raw.trim()).isEmpty()) continue;
                try {
                    Identifier id;
                    Identifier identifier2 = id = s.indexOf(58) >= 0 ? Identifier.tryParse((String)s) : Identifier.ofVanilla((String)s);
                    if (id == null || !Registries.ITEM.containsId(id)) {
                        bad.add(s);
                        continue;
                    }
                    Item item = (Item)Registries.ITEM.get(id);
                    if (item == null || item == Items.AIR) {
                        bad.add(s);
                        continue;
                    }
                    if (out.contains(item)) continue;
                    out.add(item);
                }
                catch (Throwable t) {
                    bad.add(s);
                }
            }
        }
        catch (Throwable t) {
            return List.of();
        }
        if (!bad.isEmpty()) {
            FOElytraLog.warn("\u98df\u7269\u4f18\u5148\u7ea7\uff1a\u8ba4\u4e0d\u51fa\u6765\u7684\u8df3\u8fc7\uff08%s\uff09", String.join((CharSequence)"\u3001", bad));
        }
        return List.copyOf(out);
    }

    public static int rank(List<Item> priority, ItemStack stack) {
        if (priority == null || priority.isEmpty() || stack == null || stack.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        try {
            int i = priority.indexOf(stack.getItem());
            return i < 0 ? Integer.MAX_VALUE : i;
        }
        catch (Throwable t) {
            return Integer.MAX_VALUE;
        }
    }

    public static int findSlotByPriority(MinecraftClient mc, List<Item> priority) {
        if (mc == null || mc.player == null || priority == null || priority.isEmpty()) {
            return -1;
        }
        try {
            for (Item item : priority) {
                int empty;
                int hot = -1;
                int hotCount = -1;
                int bag = -1;
                int bagCount = -1;
                for (int i = 0; i < 36; ++i) {
                    ItemStack s = mc.player.getInventory().getStack(i);
                    if (s.isEmpty() || !s.isOf(item) || !ItemHelper.isFood(s)) continue;
                    if (i < 9) {
                        if (s.getCount() <= hotCount) continue;
                        hotCount = s.getCount();
                        hot = i;
                        continue;
                    }
                    if (s.getCount() <= bagCount) continue;
                    bagCount = s.getCount();
                    bag = i;
                }
                if (hot >= 0) {
                    return hot;
                }
                if (bag < 0 || (empty = InvHelper.findEmptyHotbarSlot()) < 0) continue;
                InvHelper.moveInvToHotbar(bag, empty);
                return empty;
            }
            return -1;
        }
        catch (Throwable t) {
            FOElytraLog.detailError("FoodPriority.findSlotByPriority", t);
            return -1;
        }
    }
}

