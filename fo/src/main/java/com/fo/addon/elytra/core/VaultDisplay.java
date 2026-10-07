package com.fo.addon.elytra.core;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.VaultBlockEntity;
import net.minecraft.block.vault.VaultSharedData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.List;
public final class VaultDisplay {
    private static final int SPAWNER_RADIUS_MAX = 48;
    private static final int SPAWNER_Y_HALF_MAX = 24;
    private static final int SPAWNER_READ_BUDGET = 60000;
    private static final int WORLD_Y_MIN = -64;
    private static final int WORLD_Y_MAX = 319;
    private static volatile boolean budgetWarned;
    private static volatile boolean chunkCheckWarned;
    private VaultDisplay() {
    }
    public static ItemStack displayItem(BlockPos vaultPos) {
        if (vaultPos == null) return ItemStack.EMPTY;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return ItemStack.EMPTY;
        try {
            if (!mc.world.getBlockState(vaultPos).isOf(Blocks.VAULT)) return ItemStack.EMPTY;
            if (!(mc.world.getBlockEntity(vaultPos) instanceof VaultBlockEntity vault)) return ItemStack.EMPTY;
            VaultSharedData shared = vault.getSharedData();
            if (shared == null) return ItemStack.EMPTY;
            ItemStack shown = shared.getDisplayItem();
            return shown == null ? ItemStack.EMPTY : shown;
        } catch (Throwable t) {
            FOElytraLog.detailError("VaultDisplay.displayItem(" + vaultPos.toShortString() + ")", t);
            return ItemStack.EMPTY;
        }
    }
    public static boolean displayContainsTarget(BlockPos vaultPos, List<Item> items, List<Identifier> enchantIds) {
        if (vaultPos == null) return false;
        if ((items == null || items.isEmpty()) && (enchantIds == null || enchantIds.isEmpty())) {
            return false;
        }
        ItemStack shown = displayItem(vaultPos);
        if (shown.isEmpty()) return false;
        try {
            if (items != null && items.contains(shown.getItem())) return true;
            return shown.isOf(Items.ENCHANTED_BOOK) && matchesStoredEnchantment(shown, enchantIds);
        } catch (Throwable t) {
            FOElytraLog.detailError("VaultDisplay.displayContainsTarget(" + vaultPos.toShortString() + ")", t);
            return false;
        }
    }
    public static String describe(BlockPos vaultPos) {
        ItemStack shown = displayItem(vaultPos);
        if (shown.isEmpty()) return "（无展示物）";
        try {
            if (shown.isOf(Items.ENCHANTED_BOOK)) {
                List<String> names = storedEnchantmentNames(shown);
                if (!names.isEmpty()) return "附魔书[" + String.join("、", names) + "]";
            }
            return shown.getName().getString();
        } catch (Throwable t) {
            FOElytraLog.detailError("VaultDisplay.describe(" + vaultPos.toShortString() + ")", t);
            return "（展示物名字读取失败）";
        }
    }
    public static BlockPos nearestTrialSpawner(BlockPos origin, int radius) {
        if (origin == null) return null;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return null;
        int r = radius;
        if (r > SPAWNER_RADIUS_MAX) {
            FOElytraLog.detail("VaultDisplay：刷怪笼搜索半径 %d 超过上限，夹到 %d（避免卡客户端）", radius, SPAWNER_RADIUS_MAX);
            r = SPAWNER_RADIUS_MAX;
        }
        if (r < 1) r = 1;
        int yHalf = Math.min(r, SPAWNER_Y_HALF_MAX);
        final int ox = origin.getX();
        final int oy = origin.getY();
        final int oz = origin.getZ();
        BlockPos best = null;
        long bestSq = Long.MAX_VALUE;
        int budget = SPAWNER_READ_BUDGET;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int cachedCx = Integer.MIN_VALUE;
        int cachedCz = Integer.MIN_VALUE;
        boolean cachedLoaded = false;
        for (int dyStep = 0; dyStep <= yHalf && budget > 0; dyStep++) {
            for (int dySign = 0; dySign < 2; dySign++) {
                int dy = dySign == 0 ? dyStep : -dyStep;
                if (dySign == 1 && dyStep == 0) continue;
                int y = oy + dy;
                if (y < WORLD_Y_MIN || y > WORLD_Y_MAX) continue;
                long dySq = (long) dy * dy;
                for (int ring = 0; ring <= r && budget > 0; ring++) {
                    if ((long) ring * ring + dySq > bestSq) break;
                    int lo = -ring;
                    int hi = ring;
                    for (int dx = lo; dx <= hi && budget > 0; dx++) {
                        for (int dz = lo; dz <= hi && budget > 0; dz++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                            long sq = (long) dx * dx + dySq + (long) dz * dz;
                            if (sq >= bestSq) continue;
                            budget--;
                            int x = ox + dx;
                            int z = oz + dz;
                            int cx = x >> 4;
                            int cz = z >> 4;
                            if (cx != cachedCx || cz != cachedCz) {
                                cachedCx = cx;
                                cachedCz = cz;
                                try {
                                    cachedLoaded = mc.world.isChunkLoaded(cx, cz);
                                } catch (Throwable t) {
                                    cachedLoaded = false;
                                    if (!chunkCheckWarned) {
                                        chunkCheckWarned = true;
                                        FOElytraLog.detailError("VaultDisplay.isChunkLoaded(" + cx + "," + cz + ")", t);
                                    }
                                }
                            }
                            if (!cachedLoaded) continue;
                            cursor.set(x, y, z);
                            if (!mc.world.getBlockState(cursor).isOf(Blocks.TRIAL_SPAWNER)) continue;
                            best = cursor.toImmutable();
                            bestSq = sq;
                        }
                    }
                }
            }
        }
        if (budget <= 0 && !budgetWarned) {
            budgetWarned = true;
            FOElytraLog.detail("VaultDisplay：刷怪笼搜索用完 %d 次读方块预算（半径 %d，中心 %s），"
                + "返回的是目前最近的结果；如果这个判定要频繁跑，请调小半径",
                SPAWNER_READ_BUDGET, r, origin.toShortString());
        }
        return best;
    }
    public static boolean tooCloseToSpawner(BlockPos vaultPos, int avoidRadius) {
        if (vaultPos == null) return false;
        if (avoidRadius <= 0) return false;
        try {
            int searchRadius = Math.min(avoidRadius, SPAWNER_RADIUS_MAX);
            BlockPos near = nearestTrialSpawner(vaultPos, searchRadius);
            if (near == null) return false;
            double sq = near.getSquaredDistance(vaultPos);
            double limit = (double) avoidRadius * (double) avoidRadius;
            return sq < limit;
        } catch (Throwable t) {
            FOElytraLog.detailError("VaultDisplay.tooCloseToSpawner(" + vaultPos.toShortString() + ")", t);
            return false;
        }
    }
    private static boolean matchesStoredEnchantment(ItemStack stack, List<Identifier> enchantIds) {
        if (enchantIds == null || enchantIds.isEmpty()) return false;
        ItemEnchantmentsComponent stored = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty()) return false;
        for (RegistryEntry<Enchantment> entry : stored.getEnchantments()) {
            if (entry == null) continue;
            for (Identifier id : enchantIds) {
                if (id != null && entry.matchesId(id)) return true;
            }
        }
        return false;
    }
    private static List<String> storedEnchantmentNames(ItemStack stack) {
        List<String> out = new ArrayList<>();
        ItemEnchantmentsComponent stored = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty()) return out;
        for (RegistryEntry<Enchantment> entry : stored.getEnchantments()) {
            if (entry == null) continue;
            String name = enchantDisplayName(entry);
            if (!out.contains(name)) out.add(name);
        }
        return out;
    }
    private static String enchantDisplayName(RegistryEntry<Enchantment> entry) {
        try {
            var text = entry.value().description();
            if (text != null) {
                String s = text.getString();
                if (s != null && !s.isBlank()) return s;
            }
        } catch (Throwable t) {
            FOElytraLog.detail("VaultDisplay：读魔咒名字失败（退到 ID 显示）：%s", String.valueOf(t));
        }
        String fallback = null;
        try {
            fallback = entry.getKey().map(k -> k.getValue().getPath()).orElse(null);
        } catch (Throwable ignored) {
        }
        if (fallback != null && !fallback.isBlank()) return fallback;
        try {
            return entry.getIdAsString();
        } catch (Throwable ignored) {
            return "未知魔咒";
        }
    }
}
