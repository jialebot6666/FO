package com.fo.addon;

import com.fo.addon.utils.TrashLogic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrashLogicTest {

    @Test
    void blacklistDropsListedItems() {
        assertTrue(TrashLogic.shouldDrop(TrashLogic.Mode.BLACKLIST, true, true));
    }

    @Test
    void blacklistKeepsUnlistedItems() {
        assertFalse(TrashLogic.shouldDrop(TrashLogic.Mode.BLACKLIST, true, false));
    }

    @Test
    void whitelistKeepsListedItems() {
        assertFalse(TrashLogic.shouldDrop(TrashLogic.Mode.WHITELIST, true, true));
    }

    @Test
    void whitelistDropsUnlistedItems() {
        assertTrue(TrashLogic.shouldDrop(TrashLogic.Mode.WHITELIST, true, false));
    }

    @Test
    void emptyListNeverDropsAnything() {
        // 安全保护：列表为空时无论模式如何都不丢（防止白名单误清空背包）
        assertFalse(TrashLogic.shouldDrop(TrashLogic.Mode.BLACKLIST, false, true));
        assertFalse(TrashLogic.shouldDrop(TrashLogic.Mode.WHITELIST, false, false));
        assertFalse(TrashLogic.shouldDrop(TrashLogic.Mode.WHITELIST, false, true));
    }

    @Test
    void modeToStringIsChineseForUi() {
        // 前端汉化：下拉框/UI 走 EnumSetting 的 toString
        assertEquals("白名单", TrashLogic.Mode.WHITELIST.toString());
        assertEquals("黑名单", TrashLogic.Mode.BLACKLIST.toString());
    }

    // ==================== 附魔书命中判定（V6.20 引入，V6.21 由 EnchantList 驱动规则） ====================

    private static final TrashLogic.BookRule 迅捷潜行3 =
        new TrashLogic.BookRule(java.util.Set.of("minecraft:swift_sneak"), 3);
    private static final TrashLogic.BookRule 经验修补1 =
        new TrashLogic.BookRule(java.util.Set.of("minecraft:mending"), 1);

    @Test
    void 命中_等级必须精确相等() {
        assertTrue(TrashLogic.bookMatches(List.of(迅捷潜行3), Map.of("minecraft:swift_sneak", 3)));
        // V6.23：等级语义改为精确相等——4 级（假设存在）与 2 级都不命中
        assertFalse(TrashLogic.bookMatches(List.of(迅捷潜行3), Map.of("minecraft:swift_sneak", 4)));
        assertFalse(TrashLogic.bookMatches(List.of(迅捷潜行3), Map.of("minecraft:swift_sneak", 2)));
    }

    @Test
    void 命中_设1只命中1级_不命中更高等级() {
        // 回归：用户反馈「设迅捷潜行 1，3 级也被保留」——精确语义下 1 != 3，不命中
        assertTrue(TrashLogic.bookMatches(List.of(经验修补1), Map.of("minecraft:mending", 1)));
        assertFalse(TrashLogic.bookMatches(
            List.of(new TrashLogic.BookRule(java.util.Set.of("minecraft:swift_sneak"), 1)),
            Map.of("minecraft:swift_sneak", 3)));
        assertFalse(TrashLogic.bookMatches(
            List.of(new TrashLogic.BookRule(java.util.Set.of("minecraft:swift_sneak"), 1)),
            Map.of("minecraft:swift_sneak", 2)));
    }

    @Test
    void 命中_合并书任一附魔达标即保留() {
        // 迅捷潜行1 + 经验修补1 的合并书：迅捷潜行3 不中，但经验修补命中 → 保留
        Map<String, Integer> book = Map.of("minecraft:swift_sneak", 1, "minecraft:mending", 1);
        assertTrue(TrashLogic.bookMatches(List.of(迅捷潜行3, 经验修补1), book));
        // 只有迅捷潜行3 一条规则时：等级不够 → 不中
        assertFalse(TrashLogic.bookMatches(List.of(迅捷潜行3), book));
    }

    @Test
    void 命中_规则或书为空一律不中() {
        assertFalse(TrashLogic.bookMatches(List.of(), Map.of("minecraft:mending", 1)));
        assertFalse(TrashLogic.bookMatches(null, Map.of("minecraft:mending", 1)));
        assertFalse(TrashLogic.bookMatches(List.of(经验修补1), Map.of()));
    }

    // ==================== 等级映射 → 规则（V6.22 附魔书列表设置） ====================

    @Test
    void 等级映射_只收正值各成一条规则() {
        List<TrashLogic.BookRule> rules = TrashLogic.rulesFromLevelMap(java.util.Map.of(
            "minecraft:swift_sneak", 3,
            "minecraft:mending", 1));
        assertEquals(2, rules.size());
        assertTrue(TrashLogic.bookMatches(rules, Map.of("minecraft:swift_sneak", 3)));
        assertTrue(TrashLogic.bookMatches(rules, Map.of("minecraft:mending", 1)));
        assertFalse(TrashLogic.bookMatches(rules, Map.of("minecraft:swift_sneak", 2)));
    }

    @Test
    void 等级映射_空与空值安全() {
        assertEquals(0, TrashLogic.rulesFromLevelMap(null).size());
        assertEquals(0, TrashLogic.rulesFromLevelMap(Map.of()).size());
        // 0 或负值 = 不启用
        assertEquals(0, TrashLogic.rulesFromLevelMap(java.util.Map.of("minecraft:mending", 0)).size());
    }

    @Test
    void 等级映射_端到端_映射转换后可直接判定命中() {
        // 模块里的实际链路：设置映射 → rulesFromLevelMap → bookMatches
        var map = new java.util.HashMap<String, Integer>();
        map.put("minecraft:swift_sneak", 2);
        List<TrashLogic.BookRule> rules = TrashLogic.rulesFromLevelMap(map);
        assertTrue(TrashLogic.bookMatches(rules, Map.of("minecraft:swift_sneak", 2)));
        // V6.23 精确语义：1 级与 3 级都不命中「迅捷潜行 2」
        assertFalse(TrashLogic.bookMatches(rules, Map.of("minecraft:swift_sneak", 1)));
        assertFalse(TrashLogic.bookMatches(rules, Map.of("minecraft:swift_sneak", 3)));
    }
}
