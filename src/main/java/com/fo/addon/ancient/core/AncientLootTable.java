package com.fo.addon.ancient.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 战利品表模型（纯逻辑，可单测）。
 *
 * <p>解析 MC 的战利品表 JSON（{@code data/minecraft/loot_table/**}）：pools / entries / weight /
 * rolls / functions。只覆盖古城两张表用到的部分。
 */
public final class AncientLootTable {

    /** 一条战利品条目。{@code weight} 缺省为 1；{@code type} 形如 {@code minecraft:item} / {@code minecraft:empty}。 */
    public record Entry(String type, String name, int weight, List<Map<String, Object>> functions) {
    }

    /** 一个战利品池。{@code rolls} 是原始 JSON 值（数字或 {@code {type:uniform,min,max}}）。 */
    public record Pool(int totalWeight, List<Entry> entries, Object rolls) {
    }

    public final String id;
    public final List<Pool> pools;

    private AncientLootTable(String id, List<Pool> pools) {
        this.id = id;
        this.pools = pools;
    }

    /** 按 id 加载战利品表，如 {@code chests/ancient_city}。 */
    public static AncientLootTable load(String id, AncientResourceSource source) {
        String text;
        try {
            text = source.readText("loot_table/" + id + ".json");
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        if (text == null) {
            throw new IllegalStateException("缺少战利品表资源: loot_table/" + id + ".json");
        }
        return parse(id, AncientJson.parse(text));
    }

    @SuppressWarnings("unchecked")
    private static AncientLootTable parse(String id, Object parsed) {
        Map<String, Object> json = parsed instanceof Map ? (Map<String, Object>) parsed : Map.of();
        List<Pool> pools = new ArrayList<>();

        Object poolsObj = json.get("pools");
        if (poolsObj instanceof List<?> poolList) {
            for (Object poolObj : poolList) {
                if (!(poolObj instanceof Map<?, ?> rawPool)) continue;
                Map<String, Object> poolJson = (Map<String, Object>) rawPool;

                List<Entry> entries = new ArrayList<>();
                int totalWeight = 0;
                Object entriesObj = poolJson.get("entries");
                if (entriesObj instanceof List<?> entryList) {
                    for (Object entryObj : entryList) {
                        if (!(entryObj instanceof Map<?, ?> rawEntry)) continue;
                        Map<String, Object> entryJson = (Map<String, Object>) rawEntry;

                        String type = str(entryJson.get("type"), "");
                        String name = str(entryJson.get("name"), "");
                        int weight = intOf(entryJson.get("weight"), 1);

                        List<Map<String, Object>> functions = new ArrayList<>();
                        Object fnObj = entryJson.get("functions");
                        if (fnObj instanceof List<?> fnList) {
                            for (Object f : fnList) {
                                if (f instanceof Map<?, ?> fm) {
                                    functions.add((Map<String, Object>) fm);
                                }
                            }
                        }
                        entries.add(new Entry(type, name, weight, functions));
                        totalWeight += weight;
                    }
                }
                pools.add(new Pool(totalWeight, entries, poolJson.get("rolls")));
            }
        }
        return new AncientLootTable(id, pools);
    }

    private static String str(Object o, String def) {
        return o instanceof String s ? s : def;
    }

    private static int intOf(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }
}
