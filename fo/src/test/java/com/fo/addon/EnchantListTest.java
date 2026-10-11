package com.fo.addon;

import com.fo.addon.utils.EnchantList;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnchantListTest {

    @Test
    void 数量与官方jar一致_43个() {
        // 官方客户端 jar（1.21.11 与 26.1.2 逐条一致）data/minecraft/enchantment/ 共 43 个
        assertEquals(43, EnchantList.VANILLA.size());
    }

    @Test
    void 附魔ID无重复() {
        Set<String> ids = new HashSet<>();
        EnchantList.VANILLA.forEach(e -> ids.add(e.id()));
        assertEquals(EnchantList.VANILLA.size(), ids.size());
    }

    @Test
    void 中文名无重复且全中文() {
        Set<String> names = new HashSet<>();
        for (EnchantList.Entry e : EnchantList.VANILLA) {
            assertTrue(names.add(e.name()), "中文名重复: " + e.name());
            // 全中文（无空格/字母/数字），设置项名需要稳定且可读
            assertTrue(e.name().matches("[\\u4e00-\\u9fff]+"), "非纯中文: " + e.name());
        }
    }

    @Test
    void 最高等级都在1到5之间() {
        for (EnchantList.Entry e : EnchantList.VANILLA) {
            assertTrue(e.maxLevel() >= 1 && e.maxLevel() <= 5,
                "等级越界: " + e.id() + " max=" + e.maxLevel());
        }
    }

    @Test
    void ID格式规范() {
        for (EnchantList.Entry e : EnchantList.VANILLA) {
            assertTrue(e.id().startsWith("minecraft:"), "缺命名空间: " + e.id());
            assertTrue(e.id().matches("minecraft:[a-z_]+"), "ID 格式异常: " + e.id());
        }
    }

    @Test
    void 关键附魔抽查() {
        // 古城场景核心 + 26.x 新附魔 + 等级极值抽查（数据对照官方 jar 实测）
        assertEquals(3, find("minecraft:swift_sneak").maxLevel());
        assertEquals(1, find("minecraft:mending").maxLevel());
        assertEquals(3, find("minecraft:lunge").maxLevel());       // 26.x 长矛新附魔
        assertEquals(1, find("minecraft:flame").maxLevel());
        assertEquals(5, find("minecraft:sharpness").maxLevel());
        assertEquals(4, find("minecraft:protection").maxLevel());
        assertEquals("突进", find("minecraft:lunge").name());
        assertEquals("迅捷潜行", find("minecraft:swift_sneak").name());
        assertEquals("经验修补", find("minecraft:mending").name());
    }

    private static EnchantList.Entry find(String id) {
        return EnchantList.VANILLA.stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void 不存在未知的26x专属附魔遗漏_通过官方清单比对锚点() {
        // 官方 43 个的 ID 清单锚点（1.21.11 与 26.1.2 完全一致），防手抄遗漏
        Set<String> expected = Set.of(
            "aqua_affinity", "bane_of_arthropods", "binding_curse", "blast_protection", "breach",
            "channeling", "density", "depth_strider", "efficiency", "feather_falling",
            "fire_aspect", "fire_protection", "flame", "fortune", "frost_walker",
            "impaling", "infinity", "knockback", "looting", "loyalty",
            "luck_of_the_sea", "lunge", "lure", "mending", "multishot",
            "piercing", "power", "projectile_protection", "protection", "punch",
            "quick_charge", "respiration", "riptide", "sharpness", "silk_touch",
            "smite", "soul_speed", "sweeping_edge", "swift_sneak", "thorns",
            "unbreaking", "vanishing_curse", "wind_burst");
        Set<String> actual = new HashSet<>();
        EnchantList.VANILLA.forEach(e -> actual.add(e.id().substring("minecraft:".length())));
        assertEquals(expected, actual);
    }

    @Test
    void 每个条目名字非空且不等于ID() {
        for (EnchantList.Entry e : EnchantList.VANILLA) {
            assertFalse(e.name().isBlank());
            assertFalse(e.name().equals(e.id()));
        }
    }
}
