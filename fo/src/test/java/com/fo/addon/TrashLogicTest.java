package com.fo.addon;

import com.fo.addon.utils.BookRuleIndex;
import com.fo.addon.utils.TrashLogic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    // ==================== 附魔书规则：行解析（V6.20） ====================

    @Test
    void 规则行_中文加数字解析出名称与最低等级() {
        TrashLogic.RuleLine r = TrashLogic.parseRuleLine("迅捷潜行3");
        assertTrue(r.valid());
        assertEquals("迅捷潜行", r.namePart());
        assertEquals(3, r.minLevel());
    }

    @Test
    void 规则行_空格随意() {
        TrashLogic.RuleLine r = TrashLogic.parseRuleLine("  锋利 5 ");
        assertTrue(r.valid());
        assertEquals("锋利", r.namePart());
        assertEquals(5, r.minLevel());
    }

    @Test
    void 规则行_不写数字为任意等级() {
        TrashLogic.RuleLine r = TrashLogic.parseRuleLine("经验修补");
        assertTrue(r.valid());
        assertEquals("经验修补", r.namePart());
        assertEquals(0, r.minLevel());
    }

    @Test
    void 规则行_空行与纯数字无效() {
        assertFalse(TrashLogic.parseRuleLine("").valid());
        assertFalse(TrashLogic.parseRuleLine("   ").valid());
        assertFalse(TrashLogic.parseRuleLine(null).valid());
        // 只有数字没有名称 → 无效
        assertFalse(TrashLogic.parseRuleLine("3").valid());
    }

    // ==================== 附魔书规则：命中判定（V6.20） ====================

    private static final TrashLogic.BookRule 迅捷潜行3 =
        new TrashLogic.BookRule(java.util.Set.of("minecraft:swift_sneak"), 3);
    private static final TrashLogic.BookRule 经验修补任意 =
        new TrashLogic.BookRule(java.util.Set.of("minecraft:mending"), 0);

    @Test
    void 命中_等级达标才算() {
        assertTrue(TrashLogic.bookMatches(List.of(迅捷潜行3), Map.of("minecraft:swift_sneak", 3)));
        assertTrue(TrashLogic.bookMatches(List.of(迅捷潜行3), Map.of("minecraft:swift_sneak", 4)));
        assertFalse(TrashLogic.bookMatches(List.of(迅捷潜行3), Map.of("minecraft:swift_sneak", 2)));
    }

    @Test
    void 命中_任意等级规则等级一到就中() {
        assertTrue(TrashLogic.bookMatches(List.of(经验修补任意), Map.of("minecraft:mending", 1)));
    }

    @Test
    void 命中_合并书任一附魔达标即保留() {
        // 迅捷潜行1 + 经验修补1 的合并书：迅捷潜行3 不中，但经验修补任意等级命中 → 保留
        Map<String, Integer> book = Map.of("minecraft:swift_sneak", 1, "minecraft:mending", 1);
        assertTrue(TrashLogic.bookMatches(List.of(迅捷潜行3, 经验修补任意), book));
        // 只有迅捷潜行3 一条规则时：等级不够 → 不中
        assertFalse(TrashLogic.bookMatches(List.of(迅捷潜行3), book));
    }

    @Test
    void 命中_规则或书为空一律不中() {
        assertFalse(TrashLogic.bookMatches(List.of(), Map.of("minecraft:mending", 1)));
        assertFalse(TrashLogic.bookMatches(null, Map.of("minecraft:mending", 1)));
        assertFalse(TrashLogic.bookMatches(List.of(经验修补任意), Map.of()));
    }

    // ==================== 附魔书规则：目录匹配（V6.20） ====================

    private static final List<BookRuleIndex.CatalogEntry> 目录 = List.of(
        new BookRuleIndex.CatalogEntry("minecraft:swift_sneak", "迅捷潜行"),
        new BookRuleIndex.CatalogEntry("minecraft:protection", "保护"),
        new BookRuleIndex.CatalogEntry("minecraft:fire_protection", "火焰保护"),
        new BookRuleIndex.CatalogEntry("minecraft:projectile_protection", "弹射物保护"),
        new BookRuleIndex.CatalogEntry("minecraft:smite", "亡灵杀手"),
        new BookRuleIndex.CatalogEntry("minecraft:bane_of_arthropods", "节肢杀手"),
        new BookRuleIndex.CatalogEntry("minecraft:mending", "经验修补"),
        new BookRuleIndex.CatalogEntry("minecraft:sharpness", "锋利")
    );

    @Test
    void 目录_中文名精确匹配() {
        Optional<TrashLogic.BookRule> r = BookRuleIndex.resolve("迅捷潜行3", 目录);
        assertTrue(r.isPresent());
        assertTrue(r.get().enchantIds().contains("minecraft:swift_sneak"));
        assertEquals(3, r.get().minLevel());
    }

    @Test
    void 目录_英文ID可带可不带命名空间() {
        assertTrue(BookRuleIndex.resolve("minecraft:mending", 目录).isPresent());
        assertTrue(BookRuleIndex.resolve("mending 2", 目录).isPresent());
    }

    @Test
    void 目录_模糊匹配_单个结果() {
        // 「潜行」只命中迅捷潜行
        Optional<TrashLogic.BookRule> r = BookRuleIndex.resolve("潜行", 目录);
        assertTrue(r.isPresent());
        assertEquals(java.util.Set.of("minecraft:swift_sneak"), r.get().enchantIds());
    }

    @Test
    void 目录_模糊匹配_多个结果全收() {
        // 「保护」命中 保护/火焰保护/弹射物保护 三个（全等属于包含，自然扩到全保护系）
        Optional<TrashLogic.BookRule> r = BookRuleIndex.resolve("保护", 目录);
        assertTrue(r.isPresent());
        assertEquals(3, r.get().enchantIds().size());
        assertTrue(r.get().enchantIds().contains("minecraft:protection"));
        assertTrue(r.get().enchantIds().contains("minecraft:fire_protection"));
        assertTrue(r.get().enchantIds().contains("minecraft:projectile_protection"));
    }

    @Test
    void 目录_模糊匹配_杀手命中一对() {
        Optional<TrashLogic.BookRule> r = BookRuleIndex.resolve("杀手2", 目录);
        assertTrue(r.isPresent());
        assertEquals(2, r.get().enchantIds().size());
        assertTrue(r.get().enchantIds().contains("minecraft:smite"));
        assertTrue(r.get().enchantIds().contains("minecraft:bane_of_arthropods"));
        assertEquals(2, r.get().minLevel());
    }

    @Test
    void 目录_未命中或无效行返回空() {
        assertFalse(BookRuleIndex.resolve("不存在的附魔", 目录).isPresent());
        assertFalse(BookRuleIndex.resolve("", 目录).isPresent());
        assertFalse(BookRuleIndex.resolve("3", 目录).isPresent());
    }

    @Test
    void 目录_解析到规则的行能直接用于命中判定() {
        // 端到端：resolve 出的规则直接喂 bookMatches
        Optional<TrashLogic.BookRule> r = BookRuleIndex.resolve("迅捷潜行3", 目录);
        assertTrue(r.isPresent());
        assertTrue(TrashLogic.bookMatches(List.of(r.get()), Map.of("minecraft:swift_sneak", 3)));
        assertFalse(TrashLogic.bookMatches(List.of(r.get()), Map.of("minecraft:swift_sneak", 1)));
    }
}
