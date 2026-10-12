package com.fo.addon.utils;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AutoTrash 黑白名单 + 附魔书规则纯决策逻辑（不依赖 Minecraft 运行时，可单元测试）。
 *
 * <p>参考 meteor-miku (GPL) 自动扔垃圾的设计思路自研，并增强黑白名单模式。
 *
 * <p>V6.20：打通黑白名单——命中规则的附魔书视同「在名单里」，统一走 shouldDrop。
 * V6.21：附魔书规则由「文本列表」改为「每个附魔一条等级设置」（{@link EnchantList} 数据表），
 * 文本解析逻辑删除。
 */
public final class TrashLogic {

    public enum Mode {
        /** 黑名单：列表中的物品丢弃，其余保留 */
        BLACKLIST("黑名单"),
        /** 白名单：仅保留列表中的物品，其余全部丢弃 */
        WHITELIST("白名单");

        private final String display;

        Mode(String display) {
            this.display = display;
        }

        /** 前端 UI/下拉框/持久化均显示中文（Meteor EnumSetting 走 toString） */
        @Override
        public String toString() {
            return display;
        }
    }

    private TrashLogic() {}

    /**
     * 判断某物品是否应当被丢弃。
     *
     * @param mode          名单模式
     * @param listConfigured 物品列表是否非空（安全保护：列表为空时一律不丢，防止白名单误清空背包）
     * @param itemInList    物品是否在名单中（附魔书命中规则也视同在名单）
     */
    public static boolean shouldDrop(Mode mode, boolean listConfigured, boolean itemInList) {
        if (!listConfigured) return false;
        return mode == Mode.BLACKLIST ? itemInList : !itemInList;
    }

    // ==================== 附魔书规则（V6.20 引入，V6.21 起由 EnchantList 驱动） ====================

    /**
     * 已启用的附魔规则：附魔 ID（minecraft:xxx）+ 最低等级（1 = 任意等级）。
     * 通常由模块侧从 EnchantList + 各附魔等级设置构建。
     */
    public record BookRule(Set<String> enchantIds, int minLevel) {}

    /**
     * 「附魔ID → 最低等级」映射转规则列表（V6.22，来自附魔书列表设置）。
     * 等级 ≤ 0 / 空映射返回空列表（= 无规则，附魔书跟随物品名单）。
     */
    public static java.util.List<BookRule> rulesFromLevelMap(java.util.Map<String, Integer> levelMap) {
        java.util.List<BookRule> rules = new java.util.ArrayList<>();
        if (levelMap == null || levelMap.isEmpty()) return rules;
        for (java.util.Map.Entry<String, Integer> e : levelMap.entrySet()) {
            if (e.getKey() != null && e.getValue() != null && e.getValue() > 0) {
                rules.add(new BookRule(Set.of(e.getKey()), e.getValue()));
            }
        }
        return rules;
    }

    /**
     * 附魔书是否命中任一规则（任一附魔达标即命中，宽松策略，用户确认）。
     *
     * @param rules        已启用规则
     * @param bookEnchants 书内附魔：附魔 ID（minecraft:xxx）→ 等级
     */
    public static boolean bookMatches(List<BookRule> rules, Map<String, Integer> bookEnchants) {
        if (rules == null || rules.isEmpty() || bookEnchants == null || bookEnchants.isEmpty()) return false;
        for (BookRule rule : rules) {
            if (rule == null || rule.enchantIds() == null || rule.enchantIds().isEmpty()) continue;
            for (Map.Entry<String, Integer> e : bookEnchants.entrySet()) {
                if (!rule.enchantIds().contains(e.getKey())) continue;
                if (e.getValue() >= rule.minLevel()) return true;
            }
        }
        return false;
    }
}
