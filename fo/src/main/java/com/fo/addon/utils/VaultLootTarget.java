package com.fo.addon.utils;

/** 不祥宝库战利品定位的目标物品。 */
public enum VaultLootTarget {
    ENCHANTED_GOLDEN_APPLE("附魔金苹果", "minecraft:enchanted_golden_apple"),
    HEAVY_CORE("沉重核心", "minecraft:heavy_core"),
    BOTH("两者都", null);

    private final String label;
    private final String itemId;

    VaultLootTarget(String label, String itemId) {
        this.label = label;
        this.itemId = itemId;
    }

    /** 目标物品 ID；BOTH 返回 null（表示两种都要）。 */
    public String itemId() {
        return itemId;
    }

    public boolean matches(String id) {
        return switch (this) {
            case ENCHANTED_GOLDEN_APPLE -> "minecraft:enchanted_golden_apple".equals(id);
            case HEAVY_CORE -> "minecraft:heavy_core".equals(id);
            case BOTH -> "minecraft:enchanted_golden_apple".equals(id) || "minecraft:heavy_core".equals(id);
        };
    }

    /** 传给引擎的搜索目标（分号分隔的物品 ID）。 */
    public String searchSpec() {
        return switch (this) {
            case ENCHANTED_GOLDEN_APPLE -> "minecraft:enchanted_golden_apple";
            case HEAVY_CORE -> "minecraft:heavy_core";
            case BOTH -> "minecraft:enchanted_golden_apple;minecraft:heavy_core";
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
