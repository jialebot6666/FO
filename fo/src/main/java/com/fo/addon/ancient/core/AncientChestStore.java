package com.fo.addon.ancient.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 已找到的古城目标箱子记录（纯逻辑，可单测）。
 *
 * <p>只记「命中目标」的箱子——古城模块只关心附魔金苹果与迅捷潜行3 这两样，
 * 其余战利品不需要位置。记录按「作用域」分组（单人世界 / 各服务器 + 维度），
 * 避免在 A 服搜到的箱子跑到 B 服去高亮。
 *
 * <p>存储格式（手写最小 JSON，读写都不依赖第三方库）。
 * <b>v2 起每个作用域带 {@code seed}</b>：记录只属于某一次搜索的世界，渲染时按种子过滤，
 * 防止换种子/换世界后的残留记录产生假高亮。
 * <b>v3 起每个作用域带 {@code opened}（已开名单）</b>：开过的箱子坐标记下来，
 * 之后所有搜索都跳过它们——重开模块、重新搜索都不会把开过的箱子再加回来
 * （V6.16 实机问题：重扫会把开过的箱子重新标上）。开过的箱子被挖掉后记录保留（永久），
 * 因为这对功能反而有利：搜索命中被挖位置时直接跳过，不给空气位置标点。
 * <pre>
 * {"version":3,"scopes":{"&lt;scope&gt;":{"seed":123,
 *   "marks":[{"x":1,"y":2,"z":3,"label":"附魔金"}],
 *   "opened":["100,200","-5,-9"]}}}
 * </pre>
 * 读取时兼容 v1 旧格式（{@code scopes} 的值直接是数组、无 seed）与 v2（无 opened）。
 */
public final class AncientChestStore {

    // 线程约定：所有会碰内部 Map 的实例方法都加锁。
    // V6.10 实机崩溃（渲染线程 ConcurrentModificationException）暴露了「后台线程改共享状态」
    // 这类问题；模块现在改成只在客户端线程写入，这里再加一道锁，避免以后有人跨线程用时再踩。

    /** 附魔金苹果箱子。 */
    public static final String LABEL_GOLD = "附魔金";
    /** 迅捷潜行3 附魔书箱子。 */
    public static final String LABEL_SWIFT = "迅捷3";
    /** 两者都有。 */
    public static final String LABEL_BOTH = "两者";

    private static final int FORMAT_VERSION = 3;

    /** 一个目标箱子。{@code label} 取 {@link #LABEL_GOLD} / {@link #LABEL_SWIFT} / {@link #LABEL_BOTH}。 */
    public record Mark(int x, int y, int z, String label) {
    }

    private final Map<String, List<Mark>> byScope = new LinkedHashMap<>();
    /** 作用域 -> 该批记录所属的世界种子（v2 起随文件持久化；旧文件没有则视为未知）。 */
    private final Map<String, Long> scopeSeeds = new LinkedHashMap<>();
    /** 作用域 -> 已开箱子的坐标集合（v3 起；key = (x<<32)|z，只增不减，换种子时随作用域清空）。 */
    private final Map<String, Set<Long>> openedByScope = new LinkedHashMap<>();

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /** 某个作用域下的全部记录（拷贝）。 */
    public synchronized List<Mark> marks(String scope) {
        return new ArrayList<>(byScope.getOrDefault(scope, List.of()));
    }

    public synchronized int size(String scope) {
        return byScope.getOrDefault(scope, List.of()).size();
    }

    public synchronized boolean isEmpty(String scope) {
        return size(scope) == 0;
    }

    /** 新增一条；同一 (x,z) 已存在时覆盖标签并返回 false（不重复添加）。 */
    public synchronized boolean add(String scope, Mark mark) {
        List<Mark> list = byScope.computeIfAbsent(scope, k -> new ArrayList<>());
        for (int i = 0; i < list.size(); i++) {
            Mark old = list.get(i);
            if (old.x() == mark.x() && old.z() == mark.z()) {
                if (old.y() == mark.y() && old.label().equals(mark.label())) return false;
                list.set(i, mark);
                return false;
            }
        }
        list.add(mark);
        return true;
    }

    /**
     * 删除某 (x,z) 上的记录（忽略 y，因为一个箱子的水平坐标唯一）。
     *
     * @return 是否真的删掉了
     */
    public synchronized boolean remove(String scope, int x, int z) {
        List<Mark> list = byScope.get(scope);
        if (list == null) return false;
        return list.removeIf(m -> m.x() == x && m.z() == z);
    }

    /** 把 (x,z) 记入「已开名单」：之后所有搜索都会跳过它。 */
    public synchronized void markOpened(String scope, int x, int z) {
        openedByScope.computeIfAbsent(scope, k -> new LinkedHashSet<>()).add(key(x, z));
    }

    /** (x,z) 是否在「已开名单」里。 */
    public synchronized boolean isOpen(String scope, int x, int z) {
        Set<Long> set = openedByScope.get(scope);
        return set != null && set.contains(key(x, z));
    }

    /** 某作用域的「已开名单」大小。 */
    public synchronized int openedCount(String scope) {
        Set<Long> set = openedByScope.get(scope);
        return set == null ? 0 : set.size();
    }

    /** 清空某个作用域的<b>标记</b>（保留种子与已开名单）——新一轮搜索整批替换时用。 */
    public synchronized void clearMarks(String scope) {
        byScope.remove(scope);
    }

    /** 清空某个作用域的全部状态（标记 + 已开名单 + 种子）——换种子/换世界时用。 */
    public synchronized void clear(String scope) {
        byScope.remove(scope);
        scopeSeeds.remove(scope);
        openedByScope.remove(scope);
    }

    /** 记录该作用域这批结果所属的世界种子（随文件持久化）。 */
    public synchronized void setScopeSeed(String scope, long seed) {
        scopeSeeds.put(scope, seed);
    }

    /** 该作用域记录所属的世界种子；旧格式文件或未记录时返回 {@code null}。 */
    public synchronized Long scopeSeed(String scope) {
        return scopeSeeds.get(scope);
    }

    /** 按 3D 距离筛出半径内的记录（不含未命中项，保持原顺序）。 */
    public static List<Mark> within(List<Mark> marks, int px, int py, int pz, int radius) {
        List<Mark> out = new ArrayList<>();
        long r2 = (long) radius * radius;
        for (Mark m : marks) {
            long dx = (long) m.x() - px;
            long dy = (long) m.y() - py;
            long dz = (long) m.z() - pz;
            if (dx * dx + dy * dy + dz * dz <= r2) out.add(m);
        }
        return out;
    }

    // ==================== 序列化 ====================

    public synchronized String toJson() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"version\":").append(FORMAT_VERSION).append(",\"scopes\":{");
        // 作用域可能只有已开名单而没有标记（全部开完后 marks 被替换清空），两类键取并集
        Set<String> scopeNames = new LinkedHashSet<>();
        scopeNames.addAll(byScope.keySet());
        scopeNames.addAll(openedByScope.keySet());
        boolean firstScope = true;
        for (String scope : scopeNames) {
            List<Mark> marks = byScope.get(scope);
            Set<Long> opened = openedByScope.get(scope);
            if ((marks == null || marks.isEmpty()) && (opened == null || opened.isEmpty())) continue;
            if (!firstScope) sb.append(',');
            firstScope = false;
            sb.append('"').append(escape(scope)).append("\":{\"seed\":");
            Long seed = scopeSeeds.get(scope);
            sb.append(seed == null ? "null" : seed);
            sb.append(",\"marks\":[");
            if (marks != null) {
                for (int i = 0; i < marks.size(); i++) {
                    Mark m = marks.get(i);
                    if (i > 0) sb.append(',');
                    sb.append("{\"x\":").append(m.x())
                      .append(",\"y\":").append(m.y())
                      .append(",\"z\":").append(m.z())
                      .append(",\"label\":\"").append(escape(m.label())).append("\"}");
                }
            }
            sb.append("],\"opened\":[");
            if (opened != null) {
                boolean first = true;
                for (long k : opened) {
                    if (!first) sb.append(',');
                    first = false;
                    sb.append('"').append((int) (k >> 32)).append(',').append((int) k).append('"');
                }
            }
            sb.append("]}");
        }
        sb.append("}}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static AncientChestStore fromJson(String text) {
        AncientChestStore store = new AncientChestStore();
        if (text == null || text.isBlank()) return store;
        Object parsed;
        try {
            parsed = AncientJson.parse(text);
        } catch (RuntimeException e) {
            // 文件损坏时当作空记录，不要因为一个坏文件让模块起不来
            return store;
        }
        if (!(parsed instanceof Map<?, ?> root)) return store;
        Object scopesObj = ((Map<String, Object>) root).get("scopes");
        if (!(scopesObj instanceof Map<?, ?> scopes)) return store;

        for (Map.Entry<?, ?> e : scopes.entrySet()) {
            String scope = String.valueOf(e.getKey());
            if (e.getValue() instanceof List<?> list) {
                // v1 旧格式：值直接是 mark 数组（无 seed）
                readMarks(store, scope, list);
            } else if (e.getValue() instanceof Map<?, ?> rawScope) {
                Map<String, Object> obj = (Map<String, Object>) rawScope;
                if (obj.get("seed") instanceof Number n) {
                    store.setScopeSeed(scope, n.longValue());
                }
                if (obj.get("marks") instanceof List<?> list) {
                    readMarks(store, scope, list);
                }
                if (obj.get("opened") instanceof List<?> openedList) {
                    for (Object item : openedList) {
                        if (item instanceof String s) {
                            String[] parts = s.split(",");
                            if (parts.length == 2) {
                                try {
                                    store.markOpened(scope, Integer.parseInt(parts[0].trim()),
                                        Integer.parseInt(parts[1].trim()));
                                } catch (NumberFormatException ignored) {
                                    // 坏条目跳过
                                }
                            }
                        }
                    }
                }
            }
        }
        return store;
    }

    private static void readMarks(AncientChestStore store, String scope, List<?> list) {
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            Map<String, Object> m = (Map<String, Object>) raw;
            String label = m.get("label") instanceof String s ? s : LABEL_GOLD;
            store.add(scope, new Mark(
                intOf(m.get("x")), intOf(m.get("y")), intOf(m.get("z")), label));
        }
    }

    private static int intOf(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
