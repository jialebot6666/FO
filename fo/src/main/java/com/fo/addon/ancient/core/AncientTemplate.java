package com.fo.addon.ancient.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 结构模板（.nbt）加载与解析（纯逻辑，可单测）。
 *
 * <p>对应 Python 版 load_template：读取 size / palette / blocks，并抽取
 * minecraft:jigsaw 方块（保持 NBT 原始顺序，与 MC Palette.jigsaws() 一致）。
 *
 * <p>数据来源走 {@link AncientResourceSource}：测试与已解压目录用
 * {@link AncientResourceSource#directory(Path)}，mod 运行时用
 * {@link AncientResourceSource#bundledStructures()} 直接从 jar 内读。
 */
public final class AncientTemplate {

    /** 一个 jigsaw 方块：局部坐标 + 朝向 + 名称/目标/池/连接方式/放置优先级。 */
    public record JigsawBlock(int[] pos, String orientation, String name, String target,
                              String pool, String joint, int placementPriority) {
    }

    public final int[] size;
    public final List<JigsawBlock> jigsawBlocks;
    public final List<Map<String, Object>> palette;
    public final List<Map<String, Object>> blocks;

    private AncientTemplate(int[] size, List<JigsawBlock> jigsawBlocks,
                            List<Map<String, Object>> palette, List<Map<String, Object>> blocks) {
        this.size = size;
        this.jigsawBlocks = jigsawBlocks;
        this.palette = palette;
        this.blocks = blocks;
    }

    /**
     * 模板缓存。键为「数据源身份 + 结构名」，避免不同数据源的同名结构互相串用；
     * 用 {@link ConcurrentHashMap} 是因为模块在后台线程跑扫描（{@code CompletableFuture.runAsync}）。
     * 值包一层 {@link Optional} 以保留「缺失也缓存」的原语义（ConcurrentHashMap 不允许 null 值）。
     */
    private static final ConcurrentHashMap<String, Optional<AncientTemplate>> CACHE =
        new ConcurrentHashMap<>();

    /** 清空缓存（切换数据目录/测试用）。 */
    public static void clearCache() {
        CACHE.clear();
    }

    /** 加载模板（目录版，内部转数据源，保留原有签名）。 */
    public static AncientTemplate load(String location, Path structureDir) {
        return load(location, AncientResourceSource.directory(structureDir));
    }

    /** 加载模板；资源不存在返回 null。 */
    public static AncientTemplate load(String location, AncientResourceSource source) {
        String key = source.id() + '|' + location;
        Optional<AncientTemplate> cached = CACHE.get(key);
        if (cached != null) return cached.orElse(null);

        AncientTemplate parsed = parse(location, source);
        Optional<AncientTemplate> prev = CACHE.putIfAbsent(key, Optional.ofNullable(parsed));
        return prev != null ? prev.orElse(null) : parsed;
    }

    @SuppressWarnings("unchecked")
    private static AncientTemplate parse(String location, AncientResourceSource source) {
        String relative = location.replace("minecraft:", "") + ".nbt";
        byte[] raw;
        try (InputStream in = source.open(relative)) {
            if (in == null) return null;
            raw = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Map<String, Object> nbt = (Map<String, Object>) AncientNbt.read(raw);

        List<Map<String, Object>> blocks = castList(nbt.get("blocks"));
        List<Map<String, Object>> palette = castList(nbt.get("palette"));
        int[] size = new int[]{1, 1, 1};
        if (nbt.get("size") instanceof List<?> sz) {
            for (int i = 0; i < 3 && i < sz.size(); i++) size[i] = ((Number) sz.get(i)).intValue();
        }

        List<JigsawBlock> jigsaws = new ArrayList<>();
        for (Map<String, Object> block : blocks) {
            Object stateObj = block.get("state");
            int stateIdx = stateObj instanceof Number n ? n.intValue() : 0;
            Map<String, Object> state = (stateIdx >= 0 && stateIdx < palette.size())
                ? palette.get(stateIdx) : Map.of();
            if (!"minecraft:jigsaw".equals(state.get("Name"))) continue;

            Map<String, Object> nbtData = block.get("nbt") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
            Map<String, Object> props = state.get("Properties") instanceof Map<?, ?> pm
                ? (Map<String, Object>) pm : Map.of();

            int[] p = new int[]{0, 0, 0};
            if (block.get("pos") instanceof List<?> pl) {
                for (int i = 0; i < 3 && i < pl.size(); i++) p[i] = ((Number) pl.get(i)).intValue();
            }
            jigsaws.add(new JigsawBlock(
                p,
                str(props.get("orientation"), "north_up"),
                str(nbtData.get("name"), ""),
                str(nbtData.get("target"), ""),
                str(nbtData.get("pool"), ""),
                str(nbtData.get("joint"), "rollable"),
                nbtData.get("placement_priority") instanceof Number n ? n.intValue() : 0
            ));
        }

        return new AncientTemplate(size, jigsaws, palette, blocks);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object o) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object e : l) if (e instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
        }
        return out;
    }

    private static String str(Object o, String def) {
        return o instanceof String s ? s : def;
    }
}
