package com.fo.addon.ancient.core;

/** 古城战利品搜索目标类型。 */
public enum LootTarget {
    ENCHANTED_GOLDEN_APPLE("附魔金苹果"),
    SWIFT_SNEAK_3("迅捷潜行3"),
    BOTH("两者都搜索");

    private final String label;

    LootTarget(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
