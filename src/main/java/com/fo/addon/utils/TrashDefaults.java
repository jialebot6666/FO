package com.fo.addon.utils;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * AutoTrash 默认白名单（62 项，来自用户实测配置 + V4.22 加沙）。
 *
 * <p>物品以 ID 字符串定义（可在纯 JUnit 环境测试数量/内容），
 * 运行时通过 {@link #toItems()} 转换为 {@link Item} 列表。
 * 供 AutoTrash 默认值 与 ElytraCollector 联动开启共用：
 * 联动开启自动扔垃圾时强制恢复为白名单 + 此列表。
 */
public final class TrashDefaults {

    /** 默认白名单 62 项 ID：白名单模式下仅保留这些物品，其余全部丢弃 */
    public static final List<String> DEFAULT_WHITELIST_IDS = List.of(
        // V4.22: 挖沙联动场景——沙必须保留（攒满背包触发存沙），杂物被丢
        "minecraft:sand",                   // 沙
        // 第一张截图 (35 项)
        "minecraft:end_crystal",            // 末地水晶
        "minecraft:ender_chest",            // 末影箱
        "minecraft:totem_of_undying",       // 不死图腾
        "minecraft:firework_rocket",        // 烟花火箭
        "minecraft:experience_bottle",      // 附魔之瓶
        "minecraft:splash_potion",          // 喷溅药水
        "minecraft:glowstone",              // 荧石
        "minecraft:obsidian",               // 黑曜石
        "minecraft:crafting_table",         // 工作台
        "minecraft:quartz",                 // 下界石英
        "minecraft:respawn_anchor",         // 重生锚
        "minecraft:netherite_sword",        // 下界合金剑
        "minecraft:netherite_pickaxe",      // 下界合金镐
        "minecraft:netherite_shovel",       // 下界合金锹
        "minecraft:netherite_axe",          // 下界合金斧
        "minecraft:netherite_hoe",          // 下界合金锄
        "minecraft:netherite_helmet",       // 下界合金头盔
        "minecraft:netherite_chestplate",   // 下界合金胸甲
        "minecraft:netherite_leggings",     // 下界合金护腿
        "minecraft:netherite_boots",        // 下界合金靴子
        "minecraft:diamond_sword",          // 钻石剑
        "minecraft:diamond_pickaxe",        // 钻石镐
        "minecraft:diamond_shovel",         // 钻石锹
        "minecraft:diamond_axe",            // 钻石斧
        "minecraft:diamond_hoe",            // 钻石锄
        "minecraft:diamond_helmet",         // 钻石头盔
        "minecraft:diamond_chestplate",     // 钻石胸甲
        "minecraft:diamond_leggings",       // 钻石护腿
        "minecraft:diamond_boots",          // 钻石靴子
        "minecraft:piston",                 // 活塞
        "minecraft:sticky_piston",          // 黏性活塞
        "minecraft:golden_apple",           // 金苹果
        "minecraft:enchanted_golden_apple", // 附魔金苹果
        "minecraft:elytra",                 // 鞘翅
        "minecraft:redstone_block",         // 红石块
        // 第二张截图 (26 项)
        "minecraft:ender_pearl",        // 末影珍珠
        "minecraft:diamond_block",      // 钻石块
        "minecraft:diamond",            // 钻石
        "minecraft:netherite_ingot",    // 下界合金锭
        "minecraft:ancient_debris",     // 远古残骸
        "minecraft:trident",            // 三叉戟
        "minecraft:golden_carrot",      // 金胡萝卜
        "minecraft:mace",               // 重锤
        "minecraft:cobweb",             // 蜘蛛网
        "minecraft:shulker_box",        // 潜影盒
        "minecraft:white_shulker_box",
        "minecraft:orange_shulker_box",
        "minecraft:magenta_shulker_box",
        "minecraft:light_blue_shulker_box",
        "minecraft:yellow_shulker_box",
        "minecraft:lime_shulker_box",
        "minecraft:pink_shulker_box",
        "minecraft:gray_shulker_box",
        "minecraft:light_gray_shulker_box",
        "minecraft:cyan_shulker_box",
        "minecraft:purple_shulker_box",
        "minecraft:blue_shulker_box",
        "minecraft:brown_shulker_box",
        "minecraft:green_shulker_box",
        "minecraft:red_shulker_box",
        "minecraft:black_shulker_box"
    );

    private TrashDefaults() {}

    /** 运行时把 ID 列表转换为 Item 列表（需 MC 注册表已 bootstrap，仅游戏运行时调用） */
    public static List<Item> toItems() {
        List<Item> out = new ArrayList<>(DEFAULT_WHITELIST_IDS.size());
        for (String id : DEFAULT_WHITELIST_IDS) {
            Item item = Registries.ITEM.get(Identifier.tryParse(id));
            if (item != null) out.add(item);
        }
        return out;
    }

    /**
     * 联动时决定是否填默认列表：仅当用户列表为空/未配置时才填 61 项默认，
     * 用户已手动配置过的列表一律沿用（尊重用户改动）。
     */
    public static boolean shouldFillDefault(List<?> current) {
        return current == null || current.isEmpty();
    }
}
