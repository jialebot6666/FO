package com.fo.addon.ancient.core;

import com.fo.addon.ancient.core.AncientJigsawSimulator.Piece;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从 Jigsaw 拼图结果提取箱子坐标 + LootTableSeed（纯逻辑，可单测）。
 *
 * <p>复刻 zychen027 的 extract_chests_from_pieces：
 * <ol>
 *   <li>按 BFS 顺序遍历 piece，加载 NBT 模板，找 RandomizableContainer 方块（有 nbt 的），按 (y,x,z) 排序</li>
 *   <li>transform 到世界坐标</li>
 *   <li>按区块分组；每区块独立 RNG：setDecorationSeed → setFeatureSeed(index=0, step=7) → 逐箱 nextLong()</li>
 * </ol>
 */
public final class AncientChestExtractor {

    private AncientChestExtractor() {
    }

    /** 一个战利品箱子：世界坐标 + LootTableSeed + 战利品表 ID。 */
    public record Chest(int x, int y, int z, long seed, String lootTable) {
    }

    private static final Set<String> RANDOMIZABLE = buildRandomizable();

    private static Set<String> buildRandomizable() {
        Set<String> s = new HashSet<>();
        s.add("minecraft:chest");
        s.add("minecraft:trapped_chest");
        s.add("minecraft:barrel");
        s.add("minecraft:shulker_box");
        s.add("minecraft:dispenser");
        s.add("minecraft:dropper");
        s.add("minecraft:hopper");
        s.add("minecraft:brewing_stand");
        s.add("minecraft:furnace");
        s.add("minecraft:blast_furnace");
        s.add("minecraft:smoker");
        for (String c : new String[]{"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
            "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"}) {
            s.add("minecraft:" + c + "_shulker_box");
        }
        return s;
    }

    private static final class Raw {
        int x, y, z, chunkX, chunkZ;
        long seed;
        String lootTable;
    }

    /** 提取所有箱子（目录版，内部转数据源，保留原有签名）。 */
    public static List<Chest> extract(List<Piece> pieces, long worldSeed, Path structureDir) {
        return extract(pieces, worldSeed, AncientResourceSource.directory(structureDir));
    }

    /** 提取所有箱子（保持原有的分组与顺序语义）。 */
    public static List<Chest> extract(List<Piece> pieces, long worldSeed, AncientResourceSource structureSource) {
        // chunk key → 容器列表（用 (cx,cz) 排序，故先收集再排序）
        Map<Long, List<Raw>> byChunk = new LinkedHashMap<>();
        Map<Long, int[]> chunkKeys = new LinkedHashMap<>(); // key → (cx, cz)

        for (Piece piece : pieces) {
            String loc = piece.location();
            AncientTemplate tpl;
            if (loc.equals("minecraft:empty") || loc.equals("minecraft:feature") || loc.equals("unknown")) {
                continue;
            }
            if (loc.equals("minecraft:list")) {
                if (piece.element.children.isEmpty()) continue;
                tpl = AncientTemplate.load(piece.element.children.get(0).location, structureSource);
            } else {
                tpl = AncientTemplate.load(loc, structureSource);
            }
            if (tpl == null) continue;

            List<Map<String, Object>> containers = new ArrayList<>();
            for (Map<String, Object> b : tpl.blocks) {
                if (b.containsKey("nbt")) containers.add(b);
            }
            containers.sort(Comparator
                .comparingInt((Map<String, Object> b) -> posOf(b)[1])
                .thenComparingInt(b -> posOf(b)[0])
                .thenComparingInt(b -> posOf(b)[2]));

            for (Map<String, Object> b : containers) {
                int si = b.get("state") instanceof Number n ? n.intValue() : 0;
                Map<String, Object> state = (si >= 0 && si < tpl.palette.size()) ? tpl.palette.get(si) : Map.of();
                String name = state.get("Name") instanceof String s ? s : "";
                if (!RANDOMIZABLE.contains(name)) continue;

                int[] lp = posOf(b);
                int[] w = AncientGeometry.transform(lp, piece.rotation);
                int wx = w[0] + piece.position[0];
                int wy = w[1] + piece.position[1];
                int wz = w[2] + piece.position[2];

                @SuppressWarnings("unchecked")
                Map<String, Object> nbt = b.get("nbt") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
                String lt = nbt.get("LootTable") instanceof String s ? s : "";

                int cx = wx >> 4, cz = wz >> 4;
                long key = (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
                Raw raw = new Raw();
                raw.x = wx; raw.y = wy; raw.z = wz;
                raw.chunkX = cx; raw.chunkZ = cz;
                raw.lootTable = lt;
                byChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(raw);
                chunkKeys.put(key, new int[]{cx, cz});
            }
        }

        // 按 (chunkX, chunkZ) 排序（对齐 Python 的 sorted(by_chunk.items())）
        List<Long> keys = new ArrayList<>(byChunk.keySet());
        keys.sort(Comparator
            .comparingInt((Long k) -> chunkKeys.get(k)[0])
            .thenComparingInt(k -> chunkKeys.get(k)[1]));

        List<Chest> result = new ArrayList<>();
        for (long key : keys) {
            int[] cxz = chunkKeys.get(key);
            AncientXoroshiro.WorldgenRandom rng = new AncientXoroshiro.WorldgenRandom();
            long decorationSeed = rng.setDecorationSeed(worldSeed, cxz[0] * 16, cxz[1] * 16);
            rng.setFeatureSeed(decorationSeed, 0, 7);
            for (Raw c : byChunk.get(key)) {
                c.seed = rng.nextLong();
            }
            // 只保留有 lootTable 的（对齐 Python）
            for (Raw c : byChunk.get(key)) {
                if (c.lootTable != null && !c.lootTable.isEmpty()) {
                    result.add(new Chest(c.x, c.y, c.z, c.seed, c.lootTable));
                }
            }
        }
        return result;
    }

    private static int[] posOf(Map<String, Object> block) {
        int[] p = {0, 0, 0};
        if (block.get("pos") instanceof List<?> pl) {
            for (int i = 0; i < 3 && i < pl.size(); i++) p[i] = ((Number) pl.get(i)).intValue();
        }
        return p;
    }
}
