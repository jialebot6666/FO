package com.fo.addon.utils;

/**
 * 补给食物种类（纯逻辑，可单测）。V4.23 新增。
 *
 * 用户拍板：补给食物数量锁死一组（64），不可修改数量；
 * 但可修改食物种类，四选一：面包 / 金苹果 / 牛排 / 金胡萝卜。
 * 模块运行时用 {@link #itemId} 查 MC 注册表转 Item。
 */
public enum FoodType {
    BREAD("minecraft:bread", "面包"),
    GOLDEN_APPLE("minecraft:golden_apple", "金苹果"),
    COOKED_BEEF("minecraft:cooked_beef", "牛排"),
    GOLDEN_CARROT("minecraft:golden_carrot", "金胡萝卜");

    /** 物品 ID（运行时 Registries.ITEM 转 Item） */
    public final String itemId;
    /** 中文显示名（供设置描述/提示） */
    public final String label;

    FoodType(String itemId, String label) {
        this.itemId = itemId;
        this.label = label;
    }
}
