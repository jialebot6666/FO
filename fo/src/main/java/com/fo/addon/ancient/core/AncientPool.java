package com.fo.addon.ancient.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 结构拼图池（template_pool JSON）加载（纯逻辑，可单测）。
 *
 * <p>对应 Python 版 load_pool：解析 elements（按 weight 展开）+ fallback。
 * 未知池与 minecraft:empty 一律退化为单个 empty 元素（与 MC Pools.EMPTY 行为一致）。
 *
 * <p>数据来源走 {@link AncientResourceSource}，mod 运行时用
 * {@link AncientResourceSource#bundledPools()} 直接从 jar 内读。
 */
public final class AncientPool {

    /** 池元素：类型 + （single 的）结构位置 + projection + （list 的）子元素。 */
    public static final class PoolElement {
        public final String type;       // empty / single / feature / list
        public final String location;   // single 用
        public final String projection; // rigid / terrain_matching
        public final List<PoolElement> children; // list 用

        public PoolElement(String type, String location, String projection, List<PoolElement> children) {
            this.type = type;
            this.location = location;
            this.projection = projection;
            this.children = children;
        }

        public boolean isRigid() {
            return !"terrain_matching".equals(projection);
        }
    }

    public final List<PoolElement> elements;
    public final String fallback;

    private AncientPool(List<PoolElement> elements, String fallback) {
        this.elements = elements;
        this.fallback = fallback;
    }

    /**
     * 池缓存。键为「数据源身份 + 池名」，避免不同数据源的同名池互相串用；
     * 用 {@link ConcurrentHashMap} 是因为模块在后台线程跑扫描。
     * 池不存在时也会缓存 {@link #emptyPool()}（非 null），所以不需要 Optional 包装。
     */
    private static final ConcurrentHashMap<String, AncientPool> CACHE = new ConcurrentHashMap<>();

    public static void clearCache() {
        CACHE.clear();
    }

    /** 加载拼图池（目录版，内部转数据源，保留原有签名）。 */
    public static AncientPool load(String poolName, Path poolDir) {
        return load(poolName, AncientResourceSource.directory(poolDir));
    }

    /** 加载拼图池；不存在则返回 EMPTY 池。 */
    public static AncientPool load(String poolName, AncientResourceSource source) {
        String key = source.id() + '|' + poolName;
        AncientPool cached = CACHE.get(key);
        if (cached != null) return cached;

        AncientPool loaded = parse(poolName, source);
        AncientPool prev = CACHE.putIfAbsent(key, loaded);
        return prev != null ? prev : loaded;
    }

    @SuppressWarnings("unchecked")
    private static AncientPool parse(String poolName, AncientResourceSource source) {
        String relative = poolName.replace("minecraft:", "") + ".json";
        String text = readText(relative, source);
        if (text == null) {
            // 兼容旧行为：池目录多套了一层时，退回上级再试一次
            int slash = relative.indexOf('/');
            if (slash >= 0) text = readText(relative.substring(slash + 1), source);
        }
        if (text == null) return emptyPool();

        Map<String, Object> json = (Map<String, Object>) AncientJson.parse(text);

        List<PoolElement> elements = new ArrayList<>();
        if (json.get("elements") instanceof List<?> entries) {
            for (Object entryObj : entries) {
                if (!(entryObj instanceof Map<?, ?> entryMap)) continue;
                Map<String, Object> entry = (Map<String, Object>) entryMap;
                int weight = entry.get("weight") instanceof Number n ? n.intValue() : 1;
                Object elemObj = entry.get("element");
                if (!(elemObj instanceof Map<?, ?> elemMap)) continue;
                Map<String, Object> elem = (Map<String, Object>) elemMap;

                PoolElement pe = parseElement(elem);
                for (int i = 0; i < weight; i++) elements.add(pe);
            }
        }

        String fallback = json.get("fallback") instanceof String s ? s : "minecraft:empty";
        return new AncientPool(elements, fallback);
    }

    private static String readText(String relative, AncientResourceSource source) {
        try (InputStream in = source.open(relative)) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static PoolElement parseElement(Map<String, Object> elem) {
        String etype = elem.get("element_type") instanceof String s ? s : "";
        String projection = elem.get("projection") instanceof String s ? s : "rigid";

        switch (etype) {
            case "minecraft:single_pool_element":
                return new PoolElement("single",
                    elem.get("location") instanceof String s ? s : "", projection, List.of());
            case "minecraft:feature_pool_element":
                return new PoolElement("feature", "", projection, List.of());
            case "minecraft:list_pool_element": {
                List<PoolElement> subs = new ArrayList<>();
                if (elem.get("elements") instanceof List<?> subList) {
                    for (Object subObj : subList) {
                        if (!(subObj instanceof Map<?, ?> subMap)) continue;
                        Map<String, Object> sub = (Map<String, Object>) subMap;
                        Object inner = sub.get("element");
                        Map<String, Object> subElem = inner instanceof Map<?, ?> im
                            ? (Map<String, Object>) im : sub;
                        subs.add(parseElement(subElem));
                    }
                }
                return new PoolElement("list", "", projection, subs);
            }
            default:
                return new PoolElement("empty", "", projection, List.of());
        }
    }

    private static AncientPool emptyPool() {
        return new AncientPool(
            List.of(new PoolElement("empty", "", "rigid", List.of())),
            "minecraft:empty");
    }

    public static AncientPool empty() {
        return emptyPool();
    }
}
