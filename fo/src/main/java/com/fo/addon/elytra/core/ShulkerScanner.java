package com.fo.addon.elytra.core;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import java.util.ArrayList;
import java.util.List;
public final class ShulkerScanner {
    private ShulkerScanner() {
    }
    public record Entry(
        int slot,
        int fireworkStacks,
        int xpStacks,
        int xpBottles,
        int food,
        int totems,
        int elytra,
        String title
    ) {
    }
    public static List<Entry> scan(ScreenHandler handler, int containerSlots, List<net.minecraft.item.Item> foodItems) {
        List<Entry> result = new ArrayList<>();
        if (handler == null) return result;
        int limit = Math.min(containerSlots, handler.slots.size());
        for (int i = 0; i < limit; i++) {
            Slot slot = handler.slots.get(i);
            if (slot == null) continue;
            ItemStack stack = slot.getStack();
            if (stack.isEmpty() || !ItemHelper.isShulkerBox(stack)) continue;
            List<ItemStack> inner = ItemHelper.shulkerContents(stack);
            if (inner.isEmpty()) continue;
            int fireworks = 0;
            int xp = 0;
            int food = 0;
            int totems = 0;
            int elytra = 0;
            for (ItemStack s : inner) {
                if (s.isEmpty()) continue;
                if (s.isOf(Items.FIREWORK_ROCKET)) fireworks += s.getCount();
                else if (s.isOf(Items.EXPERIENCE_BOTTLE)) xp += s.getCount();
                else if (s.isOf(Items.TOTEM_OF_UNDYING)) totems += s.getCount();
                else if (s.isOf(Items.ELYTRA)) {
                    if (ItemHelper.hasEnchantment(s, net.minecraft.enchantment.Enchantments.UNBREAKING, 3)
                        && s.getDamage() < 15) {
                        elytra += s.getCount();
                    }
                } else if (matchesFood(s, foodItems)) food += s.getCount();
            }
            result.add(new Entry(
                i,
                fireworks / 64,
                xp / 64,
                xp,
                food,
                totems,
                elytra,
                stack.getName().getString()
            ));
        }
        return result;
    }
    private static boolean matchesFood(ItemStack stack, List<net.minecraft.item.Item> foodItems) {
        if (foodItems == null || foodItems.isEmpty()) return ItemHelper.isFood(stack);
        return foodItems.contains(stack.getItem());
    }
    public static List<Integer> select(List<Entry> entries, int needFireworks, int needSecond, boolean xpMode) {
        if (needFireworks <= 0 && needSecond <= 0) return List.of();
        int f = Math.max(1, needFireworks);
        int e = Math.max(1, needSecond);
        int n = entries.size();
        final int MAX = Integer.MAX_VALUE / 4;
        int[][][] dp = new int[f + 1][e + 1][2];
        for (int i = 0; i <= f; i++) {
            for (int j = 0; j <= e; j++) {
                dp[i][j][0] = MAX;
                dp[i][j][1] = 0;
            }
        }
        dp[0][0][0] = 0;
        for (int i = 0; i < n; i++) {
            Entry entry = entries.get(i);
            int a = entry.fireworkStacks();
            int b = xpMode ? entry.xpStacks() : entry.elytra();
            for (int ca = f; ca >= 0; ca--) {
                for (int cb = e; cb >= 0; cb--) {
                    if (dp[ca][cb][0] >= MAX) continue;
                    int na = Math.min(f, ca + a);
                    int nb = Math.min(e, cb + b);
                    int newCount = dp[ca][cb][0] + 1;
                    if (newCount < dp[na][nb][0]) {
                        dp[na][nb][0] = newCount;
                        dp[na][nb][1] = dp[ca][cb][1] | (1 << i);
                    }
                }
            }
        }
        if (dp[f][e][0] >= MAX) return List.of();
        int mask = dp[f][e][1];
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if ((mask & (1 << i)) != 0) result.add(i);
        }
        return result;
    }
    public static int findFoodRichest(List<Entry> entries) {
        int best = -1;
        int bestCount = 0;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).food() > bestCount) {
                bestCount = entries.get(i).food();
                best = i;
            }
        }
        return best;
    }
    public static int findTotemRichest(List<Entry> entries) {
        int best = -1;
        int bestCount = 0;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).totems() > bestCount) {
                bestCount = entries.get(i).totems();
                best = i;
            }
        }
        return best;
    }
}
