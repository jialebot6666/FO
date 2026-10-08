package com.fo.addon.elytra.core;

import com.fo.addon.elytra.core.ItemHelper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public final class ShulkerScanner {
    private ShulkerScanner() {
    }

    public static List<Entry> scan(ScreenHandler handler, int containerSlots, List<Item> foodItems) {
        ArrayList<Entry> result = new ArrayList<Entry>();
        if (handler == null) {
            return result;
        }
        int limit = Math.min(containerSlots, handler.slots.size());
        for (int i = 0; i < limit; ++i) {
            List<ItemStack> inner;
            ItemStack stack;
            Slot slot = (Slot)handler.slots.get(i);
            if (slot == null || (stack = slot.getStack()).isEmpty() || !ItemHelper.isShulkerBox(stack) || (inner = ItemHelper.shulkerContents(stack)).isEmpty()) continue;
            int fireworks = 0;
            int xp = 0;
            int food = 0;
            int totems = 0;
            int elytra = 0;
            for (ItemStack s : inner) {
                if (s.isEmpty()) continue;
                if (s.isOf(Items.FIREWORK_ROCKET)) {
                    fireworks += s.getCount();
                    continue;
                }
                if (s.isOf(Items.EXPERIENCE_BOTTLE)) {
                    xp += s.getCount();
                    continue;
                }
                if (s.isOf(Items.TOTEM_OF_UNDYING)) {
                    totems += s.getCount();
                    continue;
                }
                if (s.isOf(Items.ELYTRA)) {
                    if (!ItemHelper.hasEnchantment(s, (RegistryKey<Enchantment>)Enchantments.UNBREAKING, 3) || s.getDamage() >= 15) continue;
                    elytra += s.getCount();
                    continue;
                }
                if (!ShulkerScanner.matchesFood(s, foodItems)) continue;
                food += s.getCount();
            }
            result.add(new Entry(i, fireworks / 64, xp / 64, xp, food, totems, elytra, stack.getName().getString()));
        }
        return result;
    }

    private static boolean matchesFood(ItemStack stack, List<Item> foodItems) {
        if (foodItems == null || foodItems.isEmpty()) {
            return ItemHelper.isFood(stack);
        }
        return foodItems.contains(stack.getItem());
    }

    public static List<Integer> select(List<Entry> entries, int needFireworks, int needSecond, boolean xpMode) {
        int i;
        if (needFireworks <= 0 && needSecond <= 0) {
            return List.of();
        }
        int f = Math.max(1, needFireworks);
        int e = Math.max(0, needSecond);
        int n = entries.size();
        int MAX = 0x1FFFFFFF;
        int[][][] dp = new int[f + 1][e + 1][2];
        for (i = 0; i <= f; ++i) {
            for (int j = 0; j <= e; ++j) {
                dp[i][j][0] = 0x1FFFFFFF;
                dp[i][j][1] = 0;
            }
        }
        dp[0][0][0] = 0;
        for (i = 0; i < n; ++i) {
            Entry entry = entries.get(i);
            int a = entry.fireworkStacks();
            int b = xpMode ? entry.xpStacks() : entry.elytra();
            for (int ca = f; ca >= 0; --ca) {
                for (int cb = e; cb >= 0; --cb) {
                    int nb;
                    int na;
                    int newCount;
                    if (dp[ca][cb][0] >= 0x1FFFFFFF || (newCount = dp[ca][cb][0] + 1) >= dp[na = Math.min(f, ca + a)][nb = Math.min(e, cb + b)][0]) continue;
                    dp[na][nb][0] = newCount;
                    dp[na][nb][1] = dp[ca][cb][1] | 1 << i;
                }
            }
        }
        if (dp[f][e][0] >= 0x1FFFFFFF) {
            int bestA = -1;
            int bestB = -1;
            int bestScore = Integer.MIN_VALUE;
            for (int ca = 0; ca <= f; ++ca) {
                for (int cb = 0; cb <= e; ++cb) {
                    int score;
                    if (dp[ca][cb][0] >= 0x1FFFFFFF || ca <= 0 && cb <= 0 || (score = ca * 1000 + cb * 10 - dp[ca][cb][0]) <= bestScore) continue;
                    bestScore = score;
                    bestA = ca;
                    bestB = cb;
                }
            }
            if (bestA < 0) {
                return List.of();
            }
            int partial = dp[bestA][bestB][1];
            ArrayList<Integer> some = new ArrayList<Integer>();
            for (int i2 = 0; i2 < n; ++i2) {
                if ((partial & 1 << i2) == 0) continue;
                some.add(i2);
            }
            return some;
        }
        int mask = dp[f][e][1];
        ArrayList<Integer> result = new ArrayList<Integer>();
        for (int i3 = 0; i3 < n; ++i3) {
            if ((mask & 1 << i3) == 0) continue;
            result.add(i3);
        }
        return result;
    }

    public static int findFoodRichest(List<Entry> entries) {
        int best = -1;
        int bestCount = 0;
        for (int i = 0; i < entries.size(); ++i) {
            if (entries.get(i).food() <= bestCount) continue;
            bestCount = entries.get(i).food();
            best = i;
        }
        return best;
    }

    public static int findTotemRichest(List<Entry> entries) {
        int best = -1;
        int bestCount = 0;
        for (int i = 0; i < entries.size(); ++i) {
            if (entries.get(i).totems() <= bestCount) continue;
            bestCount = entries.get(i).totems();
            best = i;
        }
        return best;
    }

    public record Entry(int slot, int fireworkStacks, int xpStacks, int xpBottles, int food, int totems, int elytra, String title) {
    }
}

