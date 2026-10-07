package com.fo.addon.elytra.core;
import net.minecraft.item.Item;
import java.util.List;
public record SupplyOptions(
    int targetFireworkStacks,
    int targetXpBottles,
    int targetFoodCount,
    int targetTotems,
    int targetElytraCount,
    int minEnderChests,
    int maxShulkers,
    int placeRadius,
    int actionDelay,
    boolean autoPlaceEnderChest,
    boolean autoPickupEnderChest,
    boolean useBaritoneMine,
    boolean storeLoot,
    boolean freeSlotWhenFull,
    List<Item> storeItems,
    List<Item> foodItems,
    boolean debug,
    List<Item> foodPriority
) {
    public int actionDelay() {
        return Math.max(1, actionDelay);
    }
    public SupplyOptions(
        int targetFireworkStacks,
        int targetXpBottles,
        int targetFoodCount,
        int targetTotems,
        int targetElytraCount,
        int minEnderChests,
        int maxShulkers,
        int placeRadius,
        int actionDelay,
        boolean autoPlaceEnderChest,
        boolean autoPickupEnderChest,
        boolean useBaritoneMine,
        boolean storeLoot,
        List<Item> storeItems,
        List<Item> foodItems,
        boolean debug
    ) {
        this(targetFireworkStacks, targetXpBottles, targetFoodCount, targetTotems, targetElytraCount,
            minEnderChests, maxShulkers, placeRadius, actionDelay, autoPlaceEnderChest,
            autoPickupEnderChest, useBaritoneMine, storeLoot, false, storeItems, foodItems, debug, List.of());
    }
}
