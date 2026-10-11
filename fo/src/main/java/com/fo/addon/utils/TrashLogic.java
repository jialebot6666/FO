package com.fo.addon.utils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AutoTrash 黑白名单 + 附魔书规则纯决策逻辑（不依赖 Minecraft 运行时，可单元测试）。
 *
 * <p>参考 meteor-miku (GPL) 自动扔垃圾的设计思路自研，并增强黑白名单模式。
 *
 * <p>V6.20：附魔书过滤重做（用户确认方案后实施）——删除旧的「附魔书模式 + 全局等级阈值」，
 * 改为「附魔书规则」文本列表（游戏内直接打中文），每条规则 = 附魔中文名 + 可选最低等级，
 * 且打通黑白名单：命中任一规则的书视同「在名单里」，统一走 shouldDrop 的黑白名单判定。
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

    // ==================== 附魔书规则（V6.20） ====================

    /** 一行规则的解析结果：名称部分 + 最低等级（0 = 任意等级）。 */
    public record RuleLine(boolean valid, String namePart, int minLevel) {
        public static final RuleLine INVALID = new RuleLine(false, "", 0);
    }

    /** 结尾数字 = 最低等级，如「迅捷潜行3」「锋利 5」；不写数字 = 任意等级。 */
    private static final Pattern TRAILING_LEVEL = Pattern.compile("^(.*?)(\\d+)\\s*$");

    /**
     * 解析一行规则文本。
     * 「迅捷潜行3」→ (迅捷潜行, 3)；「经验修补」→ (经验修补, 0=任意)；空行/纯数字 → 无效。
     */
    public static RuleLine parseRuleLine(String line) {
        if (line == null) return RuleLine.INVALID;
        String s = line.trim();
        if (s.isEmpty()) return RuleLine.INVALID;

        Matcher m = TRAILING_LEVEL.matcher(s);
        if (m.matches()) {
            String name = m.group(1).trim();
            int level;
            try {
                level = Integer.parseInt(m.group(2));
            } catch (NumberFormatException e) {
                return RuleLine.INVALID;
            }
            if (name.isEmpty() || level < 0) return RuleLine.INVALID;
            return new RuleLine(true, name, level);
        }
        return new RuleLine(true, s, 0);
    }

    /**
     * 已解析到具体附魔的规则：附魔 ID 集合（minecraft:xxx）+ 最低等级（0 = 任意等级）。
     * 一个名称可能模糊匹配到多个附魔（如「保护」命中全部保护系），都装进同一个集合。
     */
    public record BookRule(Set<String> enchantIds, int minLevel) {}

    /**
     * 附魔书是否命中任一规则（任一附魔达标即命中，宽松策略，用户确认）。
     *
     * @param rules        已解析规则
     * @param bookEnchants 书内附魔：附魔 ID（minecraft:xxx）→ 等级
     */
    public static boolean bookMatches(List<BookRule> rules, Map<String, Integer> bookEnchants) {
        if (rules == null || rules.isEmpty() || bookEnchants == null || bookEnchants.isEmpty()) return false;
        for (BookRule rule : rules) {
            if (rule == null || rule.enchantIds() == null || rule.enchantIds().isEmpty()) continue;
            for (Map.Entry<String, Integer> e : bookEnchants.entrySet()) {
                if (!rule.enchantIds().contains(e.getKey())) continue;
                if (e.getValue() >= rule.minLevel()) return true; // minLevel 0 = 任意等级必达
            }
        }
        return false;
    }
}
