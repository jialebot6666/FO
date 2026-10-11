package com.fo.addon.ancient.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 附魔与标签数据（纯逻辑，可单测）。
 *
 * <p>数据全部来自 MC 1.21.11 客户端 jar（1.21 起附魔是数据驱动的）：
 * {@code data/minecraft/enchantment/*.json}、{@code data/minecraft/tags/enchantment/*.json}、
 * {@code data/minecraft/tags/item/*.json}，已抽取到 mod 资源 {@code fo/ancient/} 下，
 * 由 {@code index.json} 索引（jar 内无法列目录）。
 *
 * <p>只覆盖古城战利品判定用得到的部分：附魔的 weight / max_level / min_cost / max_cost /
 * supported_items / exclusive_set，以及标签的嵌套展开。
 */
public final class AncientEnchantData {

    /** 一个附魔定义。{@code minLevel} 在数据里没有对应字段，恒为 1。 */
    public record Enchantment(String id, int weight, int maxLevel, int minLevel,
                              int minCostBase, int minCostPerLevel,
                              int maxCostBase, int maxCostPerLevel,
                              String supportedItems, String exclusiveSet) {
    }

    /** 附魔来源解析模式。 */
    public enum SourceMode {
        /** 按 MC 本体语义解析（标签递归展开）。这是生产行为。 */
        MC_NATIVE,
        /**
         * 复刻参考实现（zychen027/AncientCityLootPredictor）的已知偏差，<b>仅用于与参考实现做等价性对照</b>。
         *
         * <p>偏差有两处：{@code enchant_with_levels} 忽略 options 直接用全部附魔；
         * {@code enchant_randomly} 的标签不展开嵌套（嵌套项查不到附魔定义被丢弃）。
         */
        REFERENCE_COMPAT
    }

    private static volatile AncientEnchantData cached;

    private final Map<String, Enchantment> enchants;
    private final Map<String, List<String>> enchantTagsRaw;
    private final Map<String, List<String>> itemTagsRaw;
    private final Map<String, Set<String>> itemTagResolved = new ConcurrentHashMap<>();
    private final Map<String, List<String>> enchantTagResolved = new ConcurrentHashMap<>();
    /** 附魔 id -> 它所属的全部 {@code #minecraft:exclusive_set/*} 标签，用于第二条互斥规则。 */
    private final Map<String, Set<String>> exclusiveMembership = new ConcurrentHashMap<>();

    private AncientEnchantData(Map<String, Enchantment> enchants,
                               Map<String, List<String>> enchantTagsRaw,
                               Map<String, List<String>> itemTagsRaw) {
        this.enchants = enchants;
        this.enchantTagsRaw = enchantTagsRaw;
        this.itemTagsRaw = itemTagsRaw;
        buildExclusiveMembership();
    }

    private void buildExclusiveMembership() {
        for (Map.Entry<String, List<String>> e : enchantTagsRaw.entrySet()) {
            if (!e.getKey().contains("exclusive_set/")) continue;
            for (String value : e.getValue()) {
                if (value.startsWith("#")) continue;
                exclusiveMembership
                    .computeIfAbsent(value, k -> ConcurrentHashMap.newKeySet())
                    .add(e.getKey());
            }
        }
    }

    /** 取 mod 内置数据的单例（懒加载）。 */
    public static AncientEnchantData bundled() {
        AncientEnchantData local = cached;
        if (local == null) {
            synchronized (AncientEnchantData.class) {
                local = cached;
                if (local == null) {
                    local = load(AncientResourceSource.bundledAncient());
                    cached = local;
                }
            }
        }
        return local;
    }

    /** 从数据源加载（测试可传自定义数据源）。 */
    public static AncientEnchantData load(AncientResourceSource source) {
        Map<String, Object> index = asMap(parse(read(source, "index.json")));

        Map<String, Enchantment> enchants = new LinkedHashMap<>();
        for (Object idObj : asList(index.get("enchantments"))) {
            String id = (String) idObj;
            String name = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
            Map<String, Object> json = asMap(parse(read(source, "enchantment/" + name + ".json")));
            enchants.put(id, new Enchantment(
                id,
                intOf(json.get("weight"), 1),
                intOf(json.get("max_level"), 1),
                1,
                intOf(asMap(json.get("min_cost")).get("base"), 0),
                intOf(asMap(json.get("min_cost")).get("per_level_above_first"), 0),
                intOf(asMap(json.get("max_cost")).get("base"), 0),
                intOf(asMap(json.get("max_cost")).get("per_level_above_first"), 0),
                strOf(json.get("supported_items"), ""),
                strOf(json.get("exclusive_set"), "")
            ));
        }

        Map<String, List<String>> enchantTags = new LinkedHashMap<>();
        for (Object idObj : asList(index.get("enchantmentTags"))) {
            String id = (String) idObj;
            // 键统一带 '#'，因为 options / supported_items 里的引用都是 "#minecraft:xxx" 形式
            enchantTags.put("#" + id, tagValues(parse(read(source, "tags/enchantment/" + localName(id) + ".json"))));
        }

        Map<String, List<String>> itemTags = new LinkedHashMap<>();
        for (Object idObj : asList(index.get("itemTags"))) {
            String id = (String) idObj;
            itemTags.put("#" + id, tagValues(parse(read(source, "tags/item/" + localName(id) + ".json"))));
        }

        return new AncientEnchantData(enchants, enchantTags, itemTags);
    }

    /** 清空单例缓存（测试用）。 */
    public static void clearCache() {
        cached = null;
    }

    // ---- 查询 ----

    public Enchantment enchant(String id) {
        return enchants.get(id);
    }

    /** 全部附魔 id（按 index.json 的顺序）。 */
    public List<String> allEnchantIds() {
        return new ArrayList<>(enchants.keySet());
    }

    /** 物品是否被该附魔支持；{@code supportedItems} 可能是 {@code #tag}。 */
    public boolean isItemSupported(String itemId, String supportedItems) {
        if (supportedItems == null || supportedItems.isEmpty()) return false;
        if (supportedItems.startsWith("#")) {
            return resolveItemTag(supportedItems).contains(itemId);
        }
        return supportedItems.equals(itemId);
    }

    /** 展开物品标签（递归），去掉 {@code #} 前缀后返回具体物品 id 集合。 */
    public Set<String> resolveItemTag(String tag) {
        return itemTagResolved.computeIfAbsent(tag, t -> {
            Set<String> out = new LinkedHashSet<>();
            collectItemTag(t, out, new LinkedHashSet<>());
            return out;
        });
    }

    private void collectItemTag(String tag, Set<String> out, Set<String> visiting) {
        if (!visiting.add(tag)) return; // 防环
        for (String v : itemTagsRaw.getOrDefault(tag, List.of())) {
            if (v.startsWith("#")) {
                collectItemTag(v, out, visiting);
            } else {
                out.add(v);
            }
        }
        visiting.remove(tag);
    }

    /**
     * 把 {@code enchant_with_levels} 的 {@code options} 解析成候选附魔列表（顺序即加权抽取顺序）。
     *
     * <p>参考实现的偏差：该函数<b>整个忽略 options</b>，直接拿全部附魔当候选。
     */
    public List<String> resolveLevelsSource(Object option, SourceMode mode) {
        if (mode == SourceMode.REFERENCE_COMPAT) return allEnchantIds();
        return resolveOption(option, mode);
    }

    /**
     * 把 {@code enchant_randomly} 的 {@code options} 解析成候选附魔列表。
     *
     * <p>参考实现的偏差：标签<b>不展开嵌套</b>，只取标签文件里的原始 values
     * （嵌套的 {@code #...} 项会在查附魔定义时被丢弃）。
     */
    public List<String> resolveRandomSource(Object option, SourceMode mode) {
        if (mode == SourceMode.REFERENCE_COMPAT && option instanceof String s
            && s.startsWith("#") && enchantTagsRaw.containsKey(s)) {
            return List.copyOf(enchantTagsRaw.get(s));
        }
        return resolveOption(option, mode);
    }

    /** 通用解析：具体 id 原样返回，{@code #标签} 递归展开，null 表示全部附魔。 */
    public List<String> resolveOption(Object option, SourceMode mode) {
        if (option == null) return allEnchantIds();
        if (option instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                out.addAll(resolveOption(o, mode));
            }
            return out;
        }
        String s = String.valueOf(option);
        if (!s.startsWith("#")) return List.of(s);
        return resolveEnchantTag(s);
    }

    /** 展开附魔标签（递归、保序、去重后按首次出现顺序返回）。 */
    public List<String> resolveEnchantTag(String tag) {
        return enchantTagResolved.computeIfAbsent(tag, t -> {
            List<String> out = new ArrayList<>();
            collectEnchantTag(t, out, new LinkedHashSet<>(), new LinkedHashSet<>());
            return out;
        });
    }

    private void collectEnchantTag(String tag, List<String> out, Set<String> seen, Set<String> visiting) {
        if (!visiting.add(tag)) return; // 防环
        for (String v : enchantTagsRaw.getOrDefault(tag, List.of())) {
            if (v.startsWith("#")) {
                collectEnchantTag(v, out, seen, visiting);
            } else if (seen.add(v)) {
                out.add(v);
            }
        }
        visiting.remove(tag);
    }

    /**
     * 两个附魔是否互斥（不能同时出现在一件物品上）。
     *
     * <p>两条规则，与 MC / 参考实现一致：
     * <ol>
     *   <li>两者的 {@code exclusive_set} 非空且<b>相同</b>（既可能是具体附魔 id，也可能是 {@code #标签}）；</li>
     *   <li>两者同属某个 {@code #minecraft:exclusive_set/*} 标签
     *       —— 这条用于兜住「自己没写 exclusive_set 字段、但被别的附魔的互斥集列进去」的情况
     *       （例如 {@code loyalty} / {@code channeling}）。</li>
     * </ol>
     */
    public boolean areCompatible(String a, String b) {
        Enchantment ea = enchants.get(a);
        Enchantment eb = enchants.get(b);
        if (ea == null || eb == null) return true;

        String sa = ea.exclusiveSet();
        if (!sa.isEmpty() && sa.equals(eb.exclusiveSet())) return false;

        Set<String> ma = exclusiveMembership.get(a);
        if (ma != null && !ma.isEmpty()) {
            Set<String> mb = exclusiveMembership.get(b);
            if (mb != null) {
                for (String tag : ma) {
                    if (mb.contains(tag)) return false;
                }
            }
        }
        return true;
    }

    // ---- 内部工具 ----

    private static String read(AncientResourceSource source, String path) {
        try {
            String text = source.readText(path);
            if (text == null) {
                throw new IllegalStateException("缺少古城数据资源: " + path);
            }
            return text;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Object parse(String text) {
        return AncientJson.parse(text);
    }

    private static String localName(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object o) {
        return o instanceof List ? (List<Object>) o : List.of();
    }

    /** 读 tag 文件的 values；同时兼容字符串与 {@code {"id": "...", "required": false}} 两种写法。 */
    private static List<String> tagValues(Object parsed) {
        List<String> out = new ArrayList<>();
        for (Object v : asList(asMap(parsed).get("values"))) {
            if (v instanceof String s) {
                out.add(s);
            } else if (v instanceof Map<?, ?> m) {
                Object id = m.get("id");
                if (id instanceof String s) out.add(s);
            }
        }
        return out;
    }

    private static int intOf(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    private static String strOf(Object o, String def) {
        return o instanceof String s ? s : def;
    }
}
