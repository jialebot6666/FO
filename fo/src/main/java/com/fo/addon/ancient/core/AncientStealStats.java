package com.fo.addon.ancient.core;

import com.fo.addon.utils.AncientStealLogic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「自动偷取」累计统计（V6.27）。
 *
 * <p>按用户要求：**写本地文件、跨重启保留、不自动清零（长期累加）**，并在设置页
 * 「自动偷取」开关的正下方显示：
 * <pre>
 * 您已使用本插件共计获取了
 * N 个附魔金苹果
 * M 迅捷潜行III
 * </pre>
 *
 * <p>键用**物品 ID / 附魔 ID**（不用显示文案），这样以后改文案或加统计项都不用迁移旧数据。
 * 附魔书按**等级分开计**：默认只偷 3 级，所以通常只有第五行的 III；用户若把
 * 「迅捷潜行最低等级」调低、确实偷到过低级书，才会多出 II / I 两行（为 0 的等级不显示）。
 *
 * <p>这里只做纯逻辑（计数 / 展示文案 / 序列化），可单测；文件读写见 {@link StealStatsFile}。
 */
public final class AncientStealStats {

    /** 统计项：附魔金苹果。 */
    public static final String GOLDEN_APPLE_ID = "minecraft:enchanted_golden_apple";

    /** 统计项：迅捷潜行附魔书（与偷取判定共用同一个附魔 ID）。 */
    public static final String SWIFT_SNEAK_ID = AncientStealLogic.SWIFT_SNEAK_ID;

    private static final int FORMAT_VERSION = 1;

    /**
     * 全局单例：模块负责写入、设置页负责读取。
     *
     * <p>测试请直接 {@code new AncientStealStats()} —— 用单例会让用例之间互相污染。
     */
    private static final AncientStealStats INSTANCE = new AncientStealStats();

    public static AncientStealStats get() {
        return INSTANCE;
    }

    private final Map<String, Integer> itemCounts = new LinkedHashMap<>();

    /** 附魔 ID → 等级 → 本数。 */
    private final Map<String, Map<Integer, Integer>> bookCounts = new LinkedHashMap<>();

    // ==================== 计数 ====================

    public synchronized void addItem(String itemId, int count) {
        if (itemId == null || count <= 0) return;
        itemCounts.merge(itemId, count, Integer::sum);
    }

    public synchronized void addBook(String enchantId, int level, int count) {
        if (enchantId == null || level <= 0 || count <= 0) return;
        bookCounts.computeIfAbsent(enchantId, k -> new LinkedHashMap<>()).merge(level, count, Integer::sum);
    }

    public synchronized int itemCount(String itemId) {
        Integer n = itemCounts.get(itemId);
        return n == null ? 0 : n;
    }

    public synchronized int bookCount(String enchantId, int level) {
        Map<Integer, Integer> byLevel = bookCounts.get(enchantId);
        if (byLevel == null) return 0;
        Integer n = byLevel.get(level);
        return n == null ? 0 : n;
    }

    /** 记录过的物品种类数（供调试与将来扩展用）。 */
    public synchronized int distinctItems() {
        return itemCounts.size();
    }

    public synchronized void clear() {
        itemCounts.clear();
        bookCounts.clear();
    }

    /** 用另一份统计整体覆盖自己（启动时把文件内容灌进单例）。 */
    public synchronized void replaceWith(AncientStealStats other) {
        clear();
        if (other == null) return;
        itemCounts.putAll(other.itemCounts);
        for (Map.Entry<String, Map<Integer, Integer>> e : other.bookCounts.entrySet()) {
            bookCounts.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
        }
    }

    // ==================== 展示 ====================

    /**
     * 设置页里的**抬头行**（固定文案）。
     *
     * <p>展示时它当"框"的标题用（{@code WSection} 的标题），计数行放在框内 —— 这样文字左对齐、
     * 有内边距，比把几行裸标签堆在设置行右侧整齐得多。
     */
    public synchronized String displayHeader() {
        return "您已使用本插件共计获取了";
    }

    /** 展示用的一行：名称 + 数值（数值会放进只读黑框里，与「高亮半径 [256]」同款控件）。 */
    public record StatRow(String name, String value) {
    }

    /**
     * 设置页里的**内容行**（名称 + 数值）。
     *
     * <p>只有「确实偷到过」的等级才会出现，免得默认情况下挂一堆 0 出来。
     */
    public synchronized List<StatRow> displayRows() {
        List<StatRow> rows = new ArrayList<>(4);
        rows.add(new StatRow("附魔金苹果", String.valueOf(itemCount(GOLDEN_APPLE_ID))));
        rows.add(new StatRow("迅捷潜行III", String.valueOf(bookCount(SWIFT_SNEAK_ID, 3))));
        if (bookCount(SWIFT_SNEAK_ID, 2) > 0) {
            rows.add(new StatRow("迅捷潜行II", String.valueOf(bookCount(SWIFT_SNEAK_ID, 2))));
        }
        if (bookCount(SWIFT_SNEAK_ID, 1) > 0) {
            rows.add(new StatRow("迅捷潜行I", String.valueOf(bookCount(SWIFT_SNEAK_ID, 1))));
        }
        return rows;
    }

    /** 抬头 + 内容行（拼成纯文本，便于一次性断言或将来复用到聊天输出）。 */
    public synchronized List<String> displayLines() {
        List<String> lines = new ArrayList<>(5);
        lines.add(displayHeader());
        for (StatRow row : displayRows()) {
            lines.add(row.name() + " " + row.value());
        }
        return lines;
    }

    // ==================== 序列化 ====================

    public synchronized String toJson() {
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"version\":").append(FORMAT_VERSION).append(",\"items\":{");
        boolean first = true;
        for (Map.Entry<String, Integer> e : itemCounts.entrySet()) {
            if (e.getValue() <= 0) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":").append(e.getValue());
        }
        sb.append("},\"books\":{");
        first = true;
        for (Map.Entry<String, Map<Integer, Integer>> e : bookCounts.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":{");
            boolean firstLevel = true;
            for (Map.Entry<Integer, Integer> lv : e.getValue().entrySet()) {
                if (lv.getValue() <= 0) continue;
                if (!firstLevel) sb.append(',');
                firstLevel = false;
                sb.append('"').append(lv.getKey()).append("\":").append(lv.getValue());
            }
            sb.append('}');
        }
        sb.append("}}");
        return sb.toString();
    }

    public static AncientStealStats fromJson(String text) {
        AncientStealStats stats = new AncientStealStats();
        if (text == null || text.isBlank()) return stats;
        Object parsed;
        try {
            parsed = AncientJson.parse(text);
        } catch (RuntimeException e) {
            // 文件坏掉时当作空统计：统计只是锦上添花，绝不能让它把模块拖挂
            return stats;
        }
        if (!(parsed instanceof Map<?, ?> root)) return stats;

        if (root.get("items") instanceof Map<?, ?> items) {
            for (Map.Entry<?, ?> e : items.entrySet()) {
                if (e.getValue() instanceof Number n) {
                    stats.addItem(String.valueOf(e.getKey()), n.intValue());
                }
            }
        }
        if (root.get("books") instanceof Map<?, ?> books) {
            for (Map.Entry<?, ?> e : books.entrySet()) {
                if (!(e.getValue() instanceof Map<?, ?> byLevel)) continue;
                for (Map.Entry<?, ?> lv : byLevel.entrySet()) {
                    if (!(lv.getValue() instanceof Number n)) continue;
                    int level;
                    try {
                        level = Integer.parseInt(String.valueOf(lv.getKey()));
                    } catch (NumberFormatException ex) {
                        continue;
                    }
                    stats.addBook(String.valueOf(e.getKey()), level, n.intValue());
                }
            }
        }
        return stats;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
