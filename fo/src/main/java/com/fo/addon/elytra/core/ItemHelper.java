package com.fo.addon.elytra.core;

import com.fo.addon.elytra.core.SettingHelper;
import java.util.List;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;

public final class ItemHelper {
    private ItemHelper() {
    }

    public static boolean hasEnchantment(ItemStack stack, RegistryKey<Enchantment> key, int minLevel) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ItemEnchantmentsComponent enchants = stack.getEnchantments();
        if (enchants == null || enchants.isEmpty()) {
            return false;
        }
        for (RegistryEntry entry : enchants.getEnchantments()) {
            if (!entry.matchesKey(key) || enchants.getLevel(entry) < minLevel) continue;
            return true;
        }
        return false;
    }

    public static boolean isMending(ItemStack stack, int minLevel) {
        return ItemHelper.hasEnchantment(stack, (RegistryKey<Enchantment>)Enchantments.MENDING, minLevel);
    }

    public static ItemStack wornElytra(PlayerEntity player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
        return chest.isOf(Items.ELYTRA) ? chest : ItemStack.EMPTY;
    }

    public static int remainingDurability(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isDamageable()) {
            return -1;
        }
        return stack.getMaxDamage() - stack.getDamage();
    }

    public static int totalElytraDurability(PlayerEntity player) {
        int total = 0;
        for (int i = 0; i < 41; ++i) {
            int remain;
            ItemStack s = player.getInventory().getStack(i);
            if (!s.isOf(Items.ELYTRA) || (remain = ItemHelper.remainingDurability(s)) <= 0) continue;
            total += remain;
        }
        return total;
    }

    public static int fireworkLevel(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isOf(Items.FIREWORK_ROCKET)) {
            return 0;
        }
        FireworksComponent component = (FireworksComponent)stack.get(DataComponentTypes.FIREWORKS);
        return component == null ? 0 : component.flightDuration();
    }

    public static boolean isShulkerBox(ItemStack stack) {
        BlockItem blockItem;
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        return item instanceof BlockItem && (blockItem = (BlockItem)item).getBlock() instanceof ShulkerBoxBlock;
    }

    public static List<ItemStack> shulkerContents(ItemStack shulker) {
        if (!ItemHelper.isShulkerBox(shulker)) {
            return List.of();
        }
        ContainerComponent container = (ContainerComponent)shulker.get(DataComponentTypes.CONTAINER);
        if (container == null) {
            return List.of();
        }
        return container.stream().toList();
    }

    public static int countInShulker(ItemStack shulker, Item item) {
        int total = 0;
        for (ItemStack inner : ItemHelper.shulkerContents(shulker)) {
            if (inner.isEmpty() || !inner.isOf(item)) continue;
            total += inner.getCount();
        }
        return total;
    }

    public static int countUsableElytraInShulker(ItemStack shulker) {
        int total = 0;
        for (ItemStack inner : ItemHelper.shulkerContents(shulker)) {
            if (inner.isEmpty() || !inner.isOf(Items.ELYTRA) || !ItemHelper.hasEnchantment(inner, (RegistryKey<Enchantment>)Enchantments.UNBREAKING, 3) || inner.getDamage() >= 15) continue;
            total += inner.getCount();
        }
        return total;
    }

    public static int countShulkers(PlayerEntity player) {
        if (player == null) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < 36; ++i) {
            ItemStack s = player.getInventory().getStack(i);
            if (!ItemHelper.isShulkerBox(s)) continue;
            total += s.getCount();
        }
        return total;
    }

    public static int countInInventory(PlayerEntity player, Item item) {
        int total = 0;
        for (int i = 0; i < 36; ++i) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isEmpty() || !s.isOf(item)) continue;
            total += s.getCount();
        }
        return total;
    }

    public static int countInHotbar(PlayerEntity player, Item item) {
        int total = 0;
        for (int i = 0; i < 9; ++i) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isEmpty() || !s.isOf(item)) continue;
            total += s.getCount();
        }
        return total;
    }

    public static int countInBackpack(PlayerEntity player, Item item) {
        int total = 0;
        for (int i = 9; i < 36; ++i) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isEmpty() || !s.isOf(item)) continue;
            total += s.getCount();
        }
        return total;
    }

    public static int stackSize(Item item) {
        int max = item.getMaxCount();
        return max <= 0 ? 64 : max;
    }

    public static int toStacks(Item item, int count) {
        int per = ItemHelper.stackSize(item);
        return (count + per - 1) / per;
    }

    public static boolean isFood(ItemStack stack) {
        return !stack.isEmpty() && SettingHelper.isFood(stack.getItem());
    }
}

