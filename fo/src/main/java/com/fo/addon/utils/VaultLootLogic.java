package com.fo.addon.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/** 不祥宝库战利品预测的纯逻辑（可单测）。 */
public final class VaultLootLogic {
    private VaultLootLogic() {
    }

    /** 一次开箱里的一件物品。 */
    public record ItemCount(String id, int count) {
    }

    /** 第 offset 次开箱（1 起）的物品清单。 */
    public record Prediction(int offset, List<ItemCount> items) {
        public boolean contains(String id) {
            for (ItemCount item : items) {
                if (item.id().equals(id)) return true;
            }
            return false;
        }
    }

    /** 目标物品在第 offset 次开箱里出现。 */
    public record Occurrence(int offset, String id, int count) {
    }

    /** 引擎 search 模式返回的一条命中。 */
    public record Hit(int offset, String id, int count) {
    }

    /** search 命中 → 出现记录（引擎已按目标过滤，这里只做类型转换）。 */
    public static List<Occurrence> occurrencesFromHits(List<Hit> hits) {
        List<Occurrence> out = new ArrayList<>();
        for (Hit hit : hits) {
            out.add(new Occurrence(hit.offset(), hit.id(), hit.count()));
        }
        return out;
    }

    /** 从预测结果里按顺序挑出出现目标物品的次序号。 */
    public static List<Occurrence> occurrences(List<Prediction> predictions, VaultLootTarget target) {
        List<Occurrence> out = new ArrayList<>();
        for (Prediction p : predictions) {
            for (ItemCount item : p.items()) {
                if (target.matches(item.id())) {
                    out.add(new Occurrence(p.offset(), item.id(), item.count()));
                }
            }
        }
        return out;
    }

    /** 取前 n 条。 */
    public static List<Occurrence> firstN(List<Occurrence> all, int n) {
        if (all.size() <= n) return all;
        return new ArrayList<>(all.subList(0, n));
    }

    /** 中文物品名（不依赖客户端语言，写死两个目标物品）。 */
    public static String itemLabel(String id) {
        return switch (id) {
            case "minecraft:enchanted_golden_apple" -> "附魔金苹果";
            case "minecraft:heavy_core" -> "沉重核心";
            default -> id;
        };
    }

    /** 一行提示：第 4 次开 → 附魔金苹果（还差 4 次）。 */
    public static String formatOccurrence(int current, Occurrence occurrence) {
        int gap = occurrence.offset() - current;
        return "第 " + occurrence.offset() + " 次开箱 → " + itemLabel(occurrence.id())
            + "（还差 " + gap + " 次）";
    }

    /** 生成 detect 用的物品清单串：id:count;id:count。 */
    public static String spec(List<ItemCount> items) {
        StringJoiner joiner = new StringJoiner(";");
        for (ItemCount item : items) {
            joiner.add(item.id() + ":" + item.count());
        }
        return joiner.toString();
    }

    /**
     * 距上次开箱的秒数 → 向后推算的窗口（开箱次数）。
     * 规则（用户口径）：10 秒 30 次、20 秒 60 次、40 秒 120 次、60 秒 200 次，往后每 60 秒 +200，封顶 1000。
     */
    public static int windowForSeconds(long seconds) {
        if (seconds <= 10) return 30;
        if (seconds <= 20) return 60;
        if (seconds <= 40) return 120;
        if (seconds <= 60) return 200;
        long window = ((seconds + 59) / 60) * 200;
        return (int) Math.min(1000, window);
    }

    /** 两次开箱间隔的上报文案：别人开了多少、平均速度。 */
    public static String formatGapReport(int previousOffset, int currentOffset, long elapsedSeconds) {
        int gap = currentOffset - previousOffset;
        int others = Math.max(0, gap - 1);
        double perMinute = elapsedSeconds > 0 ? others * 60.0 / elapsedSeconds : 0;
        return "距上次 " + elapsedSeconds + " 秒：全服开了 " + gap + " 次（你 1 次，别人 " + others
            + " 次），别人平均 " + String.format(java.util.Locale.ROOT, "%.1f", perMinute) + " 次/分钟";
    }

    /** 把一次开箱的物品按 id+count 归并（检测时多 tick 重复看到同一堆的兜底）。 */
    public static List<ItemCount> mergeCounts(Map<String, Integer> byId) {
        List<ItemCount> out = new ArrayList<>();
        byId.forEach((id, count) -> out.add(new ItemCount(id, count)));
        return out;
    }
}
