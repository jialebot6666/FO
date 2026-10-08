package com.fo.addon.elytra.core;

import com.fo.addon.elytra.core.FOElytraLog;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
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
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

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
        if (vaultPos == null) {
            return ItemStack.EMPTY;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) {
            return ItemStack.EMPTY;
        }
        try {
            if (!mc.world.getBlockState(vaultPos).isOf(Blocks.VAULT)) {
                return ItemStack.EMPTY;
            }
            BlockEntity blockEntity2 = mc.world.getBlockEntity(vaultPos);
            if (!(blockEntity2 instanceof VaultBlockEntity)) {
                return ItemStack.EMPTY;
            }
            VaultBlockEntity vault = (VaultBlockEntity)blockEntity2;
            VaultSharedData shared = vault.getSharedData();
            if (shared == null) {
                return ItemStack.EMPTY;
            }
            ItemStack shown = shared.getDisplayItem();
            return shown == null ? ItemStack.EMPTY : shown;
        }
        catch (Throwable t) {
            FOElytraLog.detailError("VaultDisplay.displayItem(" + vaultPos.toShortString() + ")", t);
            return ItemStack.EMPTY;
        }
    }

    public static boolean displayContainsTarget(BlockPos vaultPos, List<Item> items, List<Identifier> enchantIds) {
        if (vaultPos == null) {
            return false;
        }
        if ((items == null || items.isEmpty()) && (enchantIds == null || enchantIds.isEmpty())) {
            return false;
        }
        ItemStack shown = VaultDisplay.displayItem(vaultPos);
        if (shown.isEmpty()) {
            return false;
        }
        try {
            if (items != null && items.contains(shown.getItem())) {
                return true;
            }
            return shown.isOf(Items.ENCHANTED_BOOK) && VaultDisplay.matchesStoredEnchantment(shown, enchantIds);
        }
        catch (Throwable t) {
            FOElytraLog.detailError("VaultDisplay.displayContainsTarget(" + vaultPos.toShortString() + ")", t);
            return false;
        }
    }

    public static String describe(BlockPos vaultPos) {
        ItemStack shown = VaultDisplay.displayItem(vaultPos);
        if (shown.isEmpty()) {
            return "\uff08\u65e0\u5c55\u793a\u7269\uff09";
        }
        try {
            List<String> names;
            if (shown.isOf(Items.ENCHANTED_BOOK) && !(names = VaultDisplay.storedEnchantmentNames(shown)).isEmpty()) {
                return "\u9644\u9b54\u4e66[" + String.join((CharSequence)"\u3001", names) + "]";
            }
            return shown.getName().getString();
        }
        catch (Throwable t) {
            FOElytraLog.detailError("VaultDisplay.describe(" + vaultPos.toShortString() + ")", t);
            return "\uff08\u5c55\u793a\u7269\u540d\u5b57\u8bfb\u53d6\u5931\u8d25\uff09";
        }
    }

    public static BlockPos nearestTrialSpawner(BlockPos origin, int radius) {
        if (origin == null) {
            return null;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) {
            return null;
        }
        int r = radius;
        if (r > 48) {
            FOElytraLog.detail("VaultDisplay\uff1a\u5237\u602a\u7b3c\u641c\u7d22\u534a\u5f84 %d \u8d85\u8fc7\u4e0a\u9650\uff0c\u5939\u5230 %d\uff08\u907f\u514d\u5361\u5ba2\u6237\u7aef\uff09", radius, 48);
            r = 48;
        }
        if (r < 1) {
            r = 1;
        }
        int yHalf = Math.min(r, 24);
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();
        BlockPos best = null;
        long bestSq = Long.MAX_VALUE;
        int budget = 60000;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int cachedCx = Integer.MIN_VALUE;
        int cachedCz = Integer.MIN_VALUE;
        boolean cachedLoaded = false;
        for (int dyStep = 0; dyStep <= yHalf && budget > 0; ++dyStep) {
            for (int dySign = 0; dySign < 2; ++dySign) {
                int y;
                int dy;
                int n = dy = dySign == 0 ? dyStep : -dyStep;
                if (dySign == 1 && dyStep == 0 || (y = oy + dy) < -64 || y > 319) continue;
                long dySq = (long)dy * (long)dy;
                for (int ring = 0; ring <= r && budget > 0 && (long)ring * (long)ring + dySq <= bestSq; ++ring) {
                    int lo = -ring;
                    int hi = ring;
                    for (int dx = lo; dx <= hi && budget > 0; ++dx) {
                        for (int dz = lo; dz <= hi && budget > 0; ++dz) {
                            int z;
                            int x;
                            long sq;
                            block13: {
                                if (Math.max(Math.abs(dx), Math.abs(dz)) != ring || (sq = (long)dx * (long)dx + dySq + (long)dz * (long)dz) >= bestSq) continue;
                                --budget;
                                x = ox + dx;
                                z = oz + dz;
                                int cx = x >> 4;
                                int cz = z >> 4;
                                if (cx != cachedCx || cz != cachedCz) {
                                    cachedCx = cx;
                                    cachedCz = cz;
                                    try {
                                        cachedLoaded = mc.world.isChunkLoaded(cx, cz);
                                    }
                                    catch (Throwable t) {
                                        cachedLoaded = false;
                                        if (chunkCheckWarned) break block13;
                                        chunkCheckWarned = true;
                                        FOElytraLog.detailError("VaultDisplay.isChunkLoaded(" + cx + "," + cz + ")", t);
                                    }
                                }
                            }
                            if (!cachedLoaded) continue;
                            cursor.set(x, y, z);
                            if (!mc.world.getBlockState((BlockPos)cursor).isOf(Blocks.TRIAL_SPAWNER)) continue;
                            best = cursor.toImmutable();
                            bestSq = sq;
                        }
                    }
                }
            }
        }
        if (budget <= 0 && !budgetWarned) {
            budgetWarned = true;
            FOElytraLog.detail("VaultDisplay\uff1a\u5237\u602a\u7b3c\u641c\u7d22\u7528\u5b8c %d \u6b21\u8bfb\u65b9\u5757\u9884\u7b97\uff08\u534a\u5f84 %d\uff0c\u4e2d\u5fc3 %s\uff09\uff0c\u8fd4\u56de\u7684\u662f\u76ee\u524d\u6700\u8fd1\u7684\u7ed3\u679c\uff1b\u5982\u679c\u8fd9\u4e2a\u5224\u5b9a\u8981\u9891\u7e41\u8dd1\uff0c\u8bf7\u8c03\u5c0f\u534a\u5f84", 60000, r, origin.toShortString());
        }
        return best;
    }

    public static boolean tooCloseToSpawner(BlockPos vaultPos, int avoidRadius) {
        if (vaultPos == null) {
            return false;
        }
        if (avoidRadius <= 0) {
            return false;
        }
        try {
            double limit;
            int searchRadius = Math.min(avoidRadius, 48);
            BlockPos near = VaultDisplay.nearestTrialSpawner(vaultPos, searchRadius);
            if (near == null) {
                return false;
            }
            double sq = near.getSquaredDistance((Vec3i)vaultPos);
            return sq < (limit = (double)avoidRadius * (double)avoidRadius);
        }
        catch (Throwable t) {
            FOElytraLog.detailError("VaultDisplay.tooCloseToSpawner(" + vaultPos.toShortString() + ")", t);
            return false;
        }
    }

    private static boolean matchesStoredEnchantment(ItemStack stack, List<Identifier> enchantIds) {
        if (enchantIds == null || enchantIds.isEmpty()) {
            return false;
        }
        ItemEnchantmentsComponent stored = (ItemEnchantmentsComponent)stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty()) {
            return false;
        }
        for (RegistryEntry entry : stored.getEnchantments()) {
            if (entry == null) continue;
            for (Identifier id : enchantIds) {
                if (id == null || !entry.matchesId(id)) continue;
                return true;
            }
        }
        return false;
    }

    private static List<String> storedEnchantmentNames(ItemStack stack) {
        ArrayList<String> out = new ArrayList<String>();
        ItemEnchantmentsComponent stored = (ItemEnchantmentsComponent)stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty()) {
            return out;
        }
        for (RegistryEntry entry : stored.getEnchantments()) {
            String name;
            if (entry == null || out.contains(name = VaultDisplay.enchantDisplayName((RegistryEntry<Enchantment>)entry))) continue;
            out.add(name);
        }
        return out;
    }

    private static String enchantDisplayName(RegistryEntry<Enchantment> entry) {
        try {
            String s;
            Text text = ((Enchantment)entry.value()).description();
            if (text != null && (s = text.getString()) != null && !s.isBlank()) {
                return s;
            }
        }
        catch (Throwable t) {
            FOElytraLog.detail("VaultDisplay\uff1a\u8bfb\u9b54\u5492\u540d\u5b57\u5931\u8d25\uff08\u9000\u5230 ID \u663e\u793a\uff09\uff1a%s", String.valueOf(t));
        }
        String fallback = null;
        try {
            fallback = entry.getKey().map(k -> k.getValue().getPath()).orElse(null);
        }
        catch (Throwable s) {
            // empty catch block
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        try {
            return entry.getIdAsString();
        }
        catch (Throwable ignored) {
            return "\u672a\u77e5\u9b54\u5492";
        }
    }
}

