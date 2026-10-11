package com.fo.addon.utils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 附魔书规则 → 具体附魔的解析索引（V6.20，纯逻辑不依赖 MC 运行时，可单元测试）。
 *
 * <p>目录数据（附魔 ID + 中文名）由模块侧从注册表构建（中文名走 Meteor Names），
 * 本类只负责匹配：英文 ID 精确命中（带/不带命名空间）→ 只命中那一个；
 * 其余按「名称包含」收集——「潜行」→ 迅捷潜行、「保护」→ 全部保护系（保护/火焰保护/爆炸保护/弹射物保护）、
 * 「杀手」→ 亡灵杀手/节肢杀手。一个查询词命中多个附魔时全部收进同一条规则。
 */
public final class BookRuleIndex {

    /** 目录条目：附魔 ID（minecraft:xxx）+ 显示名（运行时为中文本地化名）。 */
    public record CatalogEntry(String id, String name) {}

    private BookRuleIndex() {}

    /**
     * 把一行规则文本解析并匹配到目录条目。
     *
     * @param line    规则原文（如「迅捷潜行3」「经验修补」「minecraft:swift_sneak 3」）
     * @param catalog 附魔目录
     * @return 命中 → BookRule；解析失败或没匹配到任何附魔 → empty
     */
    public static Optional<TrashLogic.BookRule> resolve(String line, List<CatalogEntry> catalog) {
        TrashLogic.RuleLine parsed = TrashLogic.parseRuleLine(line);
        if (!parsed.valid() || catalog == null || catalog.isEmpty()) {
            return Optional.empty();
        }

        String q = parsed.namePart();

        // 1) 英文/ID 精确命中（带/不带 minecraft: 前缀都行）→ 只命中那一个，不扩大
        for (CatalogEntry e : catalog) {
            if (e.id().equals(q) || stripNamespace(e.id()).equals(q)) {
                return Optional.of(new TrashLogic.BookRule(java.util.Set.of(e.id()), parsed.minLevel()));
            }
        }

        // 2) 中文名包含匹配（全等也属于包含）：「保护」→ 全部保护系，「潜行」→ 迅捷潜行
        Set<String> ids = new LinkedHashSet<>();
        for (CatalogEntry e : catalog) {
            if (e.name().contains(q)) {
                ids.add(e.id());
            }
        }
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TrashLogic.BookRule(ids, parsed.minLevel()));
    }

    private static String stripNamespace(String id) {
        int i = id.indexOf(':');
        return i >= 0 ? id.substring(i + 1) : id;
    }
}
