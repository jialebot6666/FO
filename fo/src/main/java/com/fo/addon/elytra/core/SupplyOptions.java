package com.fo.addon.elytra.core;

import java.util.List;
import net.minecraft.item.Item;

public record SupplyOptions(int targetFireworkStacks, int targetXpBottles, int targetFoodCount, int targetTotems, int targetElytraCount, int minEnderChests, int maxShulkers, int placeRadius, int actionDelay, boolean autoPlaceEnderChest, boolean autoPickupEnderChest, boolean useBaritoneMine, boolean storeLoot, List<Item> storeItems, List<Item> foodItems, boolean debug, List<Item> foodPriority) {
    public SupplyOptions(int targetFireworkStacks, int targetXpBottles, int targetFoodCount, int targetTotems, int targetElytraCount, int minEnderChests, int maxShulkers, int placeRadius, int actionDelay, boolean autoPlaceEnderChest, boolean autoPickupEnderChest, boolean useBaritoneMine, boolean storeLoot, List<Item> storeItems, List<Item> foodItems, boolean debug) {
        this(targetFireworkStacks, targetXpBottles, targetFoodCount, targetTotems, targetElytraCount, minEnderChests, maxShulkers, placeRadius, actionDelay, autoPlaceEnderChest, autoPickupEnderChest, useBaritoneMine, storeLoot, storeItems, foodItems, debug, List.of());
    }

    public int actionDelay() {
        return Math.max(1, this.actionDelay);
    }
}

