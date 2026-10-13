package com.fo.addon.ancient.core;

import com.fo.addon.ancient.core.AncientEnchantAlgorithm.EnchantInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 战利品表掷骰执行器（纯逻辑，可单测）。
 *
 * <p>用箱子的 LootTableSeed 掷出战利品表里到底开出了什么。RNG 消耗顺序与 MC 完全一致：
 * 先按池掷次数，每次抽一条条目，抽中后<b>立即</b>顺序执行该条目的 functions
 * （{@code set_count} 的 uniform、{@code set_damage}、两个附魔函数都会消耗随机数），
 * 再进入下一次抽取。少消耗或多消耗一次，后面所有箱子的判定都会错位。
 *
 * <p>只做到「掷出了哪些物品」为止：容器的分格摆放（{@code shuffleAndSplitItems}）虽然也消耗随机数，
 * 但发生在所有池掷完之后，且不改变「箱子里有没有某个物品」这个结论，所以本类不实现。
 */
public final class AncientLootEvaluator {

    /** 掷出的一件物品；{@code enchantments} 只在附魔函数命中时非空。 */
    public static final class ItemStack {
        private final String id;
        private int count;
        private final List<EnchantInstance> enchantments = new ArrayList<>();

        ItemStack(String id, int count) {
            this.id = id;
            this.count = count;
        }

        /** 当前物品 id；书被附魔后会变成 {@code minecraft:enchanted_book}。 */
        public String id() {
            return id;
        }

        public int count() {
            return count;
        }

        public List<EnchantInstance> enchantments() {
            return List.copyOf(enchantments);
        }

        private void setCount(int value) {
            this.count = value;
        }
    }

    /** 掷骰过程中可变的物品（附魔书会把 id 从 book 换成 enchanted_book）。 */
    private static final class MutableStack {
        String id;
        int count;
        final List<EnchantInstance> enchantments = new ArrayList<>();

        MutableStack(String id) {
            this.id = id;
            this.count = 1;
        }

        ItemStack freeze() {
            ItemStack out = new ItemStack(id, count);
            out.enchantments.addAll(enchantments);
            return out;
        }
    }

    private AncientLootEvaluator() {
    }

    /** 按种子掷一张战利品表，返回开出的物品列表（生产行为 = MC 本体语义）。 */
    public static List<ItemStack> roll(AncientLootTable table, long seed, AncientEnchantData data) {
        return roll(table, seed, data, AncientEnchantData.SourceMode.MC_NATIVE);
    }

    /** 按种子掷一张战利品表；{@code mode} 可切到参考实现的兼容模式做等价性对照。 */
    public static List<ItemStack> roll(AncientLootTable table, long seed, AncientEnchantData data,
                                       AncientEnchantData.SourceMode mode) {
        AncientRandom rng = new AncientRandom(seed);
        List<ItemStack> out = new ArrayList<>();

        for (AncientLootTable.Pool pool : table.pools) {
            int rollCount = resolveRolls(pool.rolls(), rng);
            for (int i = 0; i < rollCount; i++) {
                if (pool.totalWeight() <= 0) break;
                int idx = rng.nextInt(pool.totalWeight());
                int cumulative = 0;
                for (AncientLootTable.Entry entry : pool.entries()) {
                    cumulative += entry.weight();
                    if (idx < cumulative) {
                        if ("minecraft:empty".equals(entry.type())) break;
                        MutableStack stack = new MutableStack(entry.name());
                        applyFunctions(stack, entry.functions(), rng, data, mode);
                        out.add(stack.freeze());
                        break;
                    }
                }
            }
        }
        return out;
    }

    /** {@code pool.rolls}：数字直接取整（不耗随机数），uniform 走 Mth.nextInt。 */
    private static int resolveRolls(Object rolls, AncientRandom rng) {
        if (rolls instanceof Number n) return n.intValue();
        if (rolls instanceof Map<?, ?> m) {
            String type = strOf(m.get("type"), "");
            if ("minecraft:uniform".equals(type)) {
                return mthNextInt(rng, intOf(m.get("min"), 0), intOf(m.get("max"), 0));
            }
            if ("minecraft:constant".equals(type)) return intOf(m.get("value"), 1);
        }
        return 1;
    }

    private static void applyFunctions(MutableStack stack, List<Map<String, Object>> functions,
                                       AncientRandom rng, AncientEnchantData data,
                                       AncientEnchantData.SourceMode mode) {
        for (Map<String, Object> func : functions) {
            String type = strOf(func.get("function"), "");
            switch (type) {
                case "minecraft:set_count" -> {
                    int newCount = numberProviderInt(func.get("count"), rng);
                    boolean add = Boolean.TRUE.equals(func.get("add"));
                    stack.count = add ? stack.count + newCount : newCount;
                }
                case "minecraft:set_damage" -> numberProviderFloat(func.get("damage"), rng);
                case "minecraft:enchant_with_levels" -> applyEnchantWithLevels(stack, func, rng, data, mode);
                case "minecraft:enchant_randomly" -> applyEnchantRandomly(stack, func, rng, data, mode);
                case "minecraft:set_potion" -> { /* 不消耗随机数 */ }
                default -> { /* 未知函数：MC 与本实现都不消耗随机数 */ }
            }
        }
    }

    private static int numberProviderInt(Object provider, AncientRandom rng) {
        if (provider instanceof Number n) return n.intValue();
        if (provider instanceof Map<?, ?> m) {
            String type = strOf(m.get("type"), "");
            if ("minecraft:uniform".equals(type)) {
                return mthNextInt(rng, numberProviderInt(m.get("min"), rng),
                    numberProviderInt(m.get("max"), rng));
            }
            if ("minecraft:constant".equals(type)) return intOf(m.get("value"), 0);
        }
        return 1;
    }

    private static double numberProviderFloat(Object provider, AncientRandom rng) {
        if (provider instanceof Number n) return n.doubleValue();
        if (provider instanceof Map<?, ?> m) {
            String type = strOf(m.get("type"), "");
            if ("minecraft:uniform".equals(type)) {
                double min = numberProviderFloat(m.get("min"), rng);
                double max = numberProviderFloat(m.get("max"), rng);
                return min + rng.nextFloat() * (max - min);
            }
            if ("minecraft:constant".equals(type)) return dblOf(m.get("value"), 0.0);
        }
        return 1.0;
    }

    /** {@code EnchantWithLevelsFunction}：levels 走整数 provider，再走附魔台算法。 */
    private static void applyEnchantWithLevels(MutableStack stack, Map<String, Object> func,
                                               AncientRandom rng, AncientEnchantData data,
                                               AncientEnchantData.SourceMode mode) {
        int cost = numberProviderInt(func.get("levels"), rng);
        // ≥1.21 用 options（如 #minecraft:on_random_loot）；≤1.20.6 用 treasure:true，
        // 语义 = 候选池为全部附魔（applicable 过滤由附魔台算法按物品完成）。
        // options 缺失时 resolveLevelsSource 恰好返回全部附魔，正好对应旧语法。
        List<String> source = data.resolveLevelsSource(func.get("options"), mode);
        apply(stack, AncientEnchantAlgorithm.select(rng, stack.id, cost, source, data));
    }

    /** {@code EnchantRandomlyFunction}：随机挑一个候选附魔，再在其等级区间内取随机等级。 */
    private static void applyEnchantRandomly(MutableStack stack, Map<String, Object> func,
                                             AncientRandom rng, AncientEnchantData data,
                                             AncientEnchantData.SourceMode mode) {
        // ≥1.21 用 options（id 或 #标签）；≤1.20.6 用旧语法的 "enchantments": [...] 列表。
        // 两者都没有时（旧语法里也允许省略）候选池 = 全部附魔。
        Object sourceOption = func.get("options");
        if (sourceOption == null) sourceOption = func.get("enchantments");
        List<String> source = data.resolveRandomSource(sourceOption, mode);
        boolean isBook = "minecraft:book".equals(stack.id);

        List<String> valid = new ArrayList<>();
        for (String enchId : source) {
            AncientEnchantData.Enchantment ench = data.enchant(enchId);
            if (ench == null) continue;
            if (isBook || data.isItemSupported(stack.id, ench.supportedItems())) {
                valid.add(enchId);
            }
        }
        if (valid.isEmpty()) return;

        AncientEnchantData.Enchantment ench = data.enchant(valid.get(rng.nextInt(valid.size())));
        if (ench == null) return;
        apply(stack, List.of(new EnchantInstance(ench.id(),
            mthNextInt(rng, ench.minLevel(), ench.maxLevel()), ench.weight())));
    }

    /** 附魔落到物品上（书会变成附魔书）。 */
    private static void apply(MutableStack stack, List<EnchantInstance> enchants) {
        if (enchants.isEmpty()) return;
        if ("minecraft:book".equals(stack.id)) {
            stack.id = "minecraft:enchanted_book";
        }
        stack.enchantments.addAll(enchants);
    }

    // ==================== 目标判定 ====================

    /** 是否开出附魔金苹果。 */
    public static boolean hasEnchantedGoldenApple(List<ItemStack> items) {
        for (ItemStack s : items) {
            if ("minecraft:enchanted_golden_apple".equals(s.id())) return true;
        }
        return false;
    }

    /** 是否开出指定等级的迅捷潜行附魔书。 */
    public static boolean hasSwiftSneak(List<ItemStack> items, int level) {
        for (ItemStack s : items) {
            if (!"minecraft:enchanted_book".equals(s.id())) continue;
            for (EnchantInstance e : s.enchantments()) {
                if ("minecraft:swift_sneak".equals(e.enchantment()) && e.level() == level) return true;
            }
        }
        return false;
    }

    /** 按搜索目标判定一个箱子：{@code BOTH} 表示两者都要。 */
    public static boolean matches(List<ItemStack> items, LootTarget target) {
        boolean gold = hasEnchantedGoldenApple(items);
        boolean swift = hasSwiftSneak(items, 3);
        return switch (target) {
            case ENCHANTED_GOLDEN_APPLE -> gold;
            case SWIFT_SNEAK_3 -> swift;
            case BOTH -> gold && swift;
        };
    }

    /** 命中标签：{@code 附魔金} / {@code 迅捷3} / {@code 两者}；都没命中返回 null。 */
    public static String label(List<ItemStack> items) {
        boolean gold = hasEnchantedGoldenApple(items);
        boolean swift = hasSwiftSneak(items, 3);
        if (gold && swift) return "两者";
        if (gold) return "附魔金";
        if (swift) return "迅捷3";
        return null;
    }

    // ---- Mth 工具（min >= max 时不消耗随机数，这一点必须保持） ----

    static int mthNextInt(AncientRandom rng, int minInclusive, int maxInclusive) {
        if (minInclusive >= maxInclusive) return minInclusive;
        return rng.nextInt(maxInclusive - minInclusive + 1) + minInclusive;
    }

    private static String strOf(Object o, String def) {
        return o instanceof String s ? s : def;
    }

    private static int intOf(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    private static double dblOf(Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }
}
