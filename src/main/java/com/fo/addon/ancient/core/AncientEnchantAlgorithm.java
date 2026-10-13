package com.fo.addon.ancient.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 附魔台算法（纯逻辑，可单测）。
 *
 * <p>复刻 MC 的 {@code EnchantmentHelper.getAvailableEnchantmentResults} /
 * {@code selectEnchantment} / {@code filterCompatibleEnchantments}，用于战利品表里
 * {@code minecraft:enchant_with_levels} 与 {@code minecraft:enchant_randomly} 两个函数。
 *
 * <p>RNG 消耗顺序必须与 MC 完全一致：{@code nextInt} ×2、{@code nextFloat} ×2、
 * 首次加权抽取 ×1、然后每次 {@code nextInt(50)} 判定 + 一次加权抽取。
 */
public final class AncientEnchantAlgorithm {

    private AncientEnchantAlgorithm() {
    }

    /** 一个「附魔 + 等级」结果。 */
    public record EnchantInstance(String enchantment, int level, int weight) {
    }

    /**
     * 物品附魔能力值（{@code Item.Properties.enchantable}），取自 MC 1.21.11。
     *
     * <p>只列古城战利品表里真的会走 {@code enchant_with_levels} 的物品——
     * 该函数在表里只出现在 {@code diamond_hoe} / {@code diamond_leggings} / {@code iron_leggings} 上。
     * 表里没有的物品按 0 处理（与 MC 一致：附魔能力为 0 时直接不附魔、且不消耗随机数）。
     *
     * <p>这三个数值由「与参考实现逐箱对照全等」间接验证（见 {@code AncientLootEvaluatorTest}）。
     */
    private static final Map<String, Integer> ITEM_ENCHANTABILITY = Map.of(
        "minecraft:diamond_hoe", 14,
        "minecraft:diamond_leggings", 10,
        "minecraft:iron_leggings", 9
    );

    public static int enchantability(String itemId) {
        return ITEM_ENCHANTABILITY.getOrDefault(itemId, 0);
    }

    /**
     * 复刻 {@code getAvailableEnchantmentResults}：给定附魔花费与候选附魔，
     * 返回所有「等级对应的花费区间覆盖该花费」的附魔（每个附魔只取满足的最高等级）。
     */
    public static List<EnchantInstance> getAvailable(int cost, String itemId, List<String> source,
                                                     AncientEnchantData data) {
        List<EnchantInstance> results = new ArrayList<>();
        boolean isBook = "minecraft:book".equals(itemId);

        for (String enchId : source) {
            AncientEnchantData.Enchantment ench = data.enchant(enchId);
            if (ench == null) continue;
            if (!isBook && !data.isItemSupported(itemId, ench.supportedItems())) continue;

            for (int level = ench.maxLevel(); level >= ench.minLevel(); level--) {
                int minCost = ench.minCostBase() + ench.minCostPerLevel() * (level - 1);
                int maxCost = ench.maxCostBase() + ench.maxCostPerLevel() * (level - 1);
                if (cost < minCost || cost > maxCost) continue;
                results.add(new EnchantInstance(enchId, level, ench.weight()));
                break;
            }
        }
        return results;
    }

    /**
     * 复刻 {@code selectEnchantment}：返回最终选中的附魔列表。
     *
     * <p>物品附魔能力为 0 时直接返回空列表，<b>且不消耗任何随机数</b>——这一点必须保持，
     * 否则整条 RNG 链会错位。
     */
    public static List<EnchantInstance> select(AncientRandom rng, String itemId, int enchantmentCost,
                                               List<String> source, AncientEnchantData data) {
        List<EnchantInstance> results = new ArrayList<>();
        int enchantable = enchantability(itemId);
        if (enchantable == 0) return results;

        int cost = enchantmentCost + 1
            + rng.nextInt(enchantable / 4 + 1)
            + rng.nextInt(enchantable / 4 + 1);

        float span = (rng.nextFloat() + rng.nextFloat() - 1.0f) * 0.15f;
        cost = Math.max(1, Math.round(cost * (1.0f + span)));

        List<EnchantInstance> available = getAvailable(cost, itemId, source, data);
        if (available.isEmpty()) return results;

        EnchantInstance first = weightedRandom(rng, available);
        if (first != null) results.add(first);

        while (rng.nextInt(50) <= cost) {
            if (!results.isEmpty()) {
                filterCompatible(available, results.get(results.size() - 1).enchantment(), data);
            }
            if (available.isEmpty()) break;
            EnchantInstance next = weightedRandom(rng, available);
            if (next != null) results.add(next);
            cost = cost / 2;
        }
        return results;
    }

    /** 复刻 {@code WeightedRandom.getRandomItem}。 */
    private static EnchantInstance weightedRandom(AncientRandom rng, List<EnchantInstance> items) {
        int totalWeight = 0;
        for (EnchantInstance e : items) totalWeight += e.weight();
        if (totalWeight <= 0) return null;

        int selection = rng.nextInt(totalWeight);
        for (EnchantInstance e : items) {
            selection -= e.weight();
            if (selection < 0) return e;
        }
        return null;
    }

    /** 复刻 {@code filterCompatibleEnchantments}：就地剔除与 target 不兼容的候选。 */
    private static void filterCompatible(List<EnchantInstance> items, String target, AncientEnchantData data) {
        items.removeIf(e -> !data.areCompatible(e.enchantment(), target));
    }
}
