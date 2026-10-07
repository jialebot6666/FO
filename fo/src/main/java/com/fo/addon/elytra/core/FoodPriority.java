package com.fo.addon.elytra.core;
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
        List<Item> out = new ArrayList<>();
        List<String> bad = new ArrayList<>();
        if (csv == null || csv.isBlank()) return out;
        try {
            for (String raw : csv.split("[,;\\s]+")) {
                if (raw == null) continue;
                String s = raw.trim();
                if (s.isEmpty()) continue;
                try {
                    Identifier id = s.indexOf(':') >= 0 ? Identifier.tryParse(s) : Identifier.ofVanilla(s);
                    if (id == null || !Registries.ITEM.containsId(id)) {
                        bad.add(s);
                        continue;
                    }
                    Item item = Registries.ITEM.get(id);
                    if (item == null || item == Items.AIR) {
                        bad.add(s);
                        continue;
                    }
                    if (!out.contains(item)) out.add(item);
                } catch (Throwable t) {
                    bad.add(s);
                }
            }
        } catch (Throwable t) {
            return List.of();
        }
        if (!bad.isEmpty()) FOElytraLog.warn("食物优先级：认不出来的跳过（%s）", String.join("、", bad));
        return List.copyOf(out);
    }
    public static int rank(List<Item> priority, ItemStack stack) {
        if (priority == null || priority.isEmpty() || stack == null || stack.isEmpty()) return Integer.MAX_VALUE;
        try {
            int i = priority.indexOf(stack.getItem());
            return i < 0 ? Integer.MAX_VALUE : i;
        } catch (Throwable t) {
            return Integer.MAX_VALUE;
        }
    }
    public static int findSlotByPriority(MinecraftClient mc, List<Item> priority) {
        if (mc == null || mc.player == null || priority == null || priority.isEmpty()) return -1;
        try {
            for (Item item : priority) {
                int hot = -1;
                int hotCount = -1;
                int bag = -1;
                int bagCount = -1;
                for (int i = 0; i < 36; i++) {
                    ItemStack s = mc.player.getInventory().getStack(i);
                    if (s.isEmpty() || !s.isOf(item) || !ItemHelper.isFood(s)) continue;
                    if (i < 9) {
                        if (s.getCount() > hotCount) {
                            hotCount = s.getCount();
                            hot = i;
                        }
                    } else if (s.getCount() > bagCount) {
                        bagCount = s.getCount();
                        bag = i;
                    }
                }
                if (hot >= 0) return hot;
                if (bag < 0) continue;
                int empty = InvHelper.findEmptyHotbarSlot();
                if (empty < 0) continue;
                InvHelper.moveInvToHotbar(bag, empty);
                return empty;
            }
            return -1;
        } catch (Throwable t) {
            FOElytraLog.detailError("FoodPriority.findSlotByPriority", t);
            return -1;
        }
    }
}
