package com.fo.addon.ancient.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 目标过滤与城市聚类。 */
class EnchantedGoldCityLogicTest {

    private static AncientChestStore.Mark mark(int x, int z, String label) {
        return new AncientChestStore.Mark(x, -50, z, label);
    }

    @Test
    void 只认附魔金和两者() {
        assertTrue(EnchantedGoldCityLogic.isGoldMark(mark(1, 1, AncientChestStore.LABEL_GOLD)));
        assertTrue(EnchantedGoldCityLogic.isGoldMark(mark(1, 1, AncientChestStore.LABEL_BOTH)));
        assertFalse(EnchantedGoldCityLogic.isGoldMark(mark(1, 1, AncientChestStore.LABEL_SWIFT)));
        assertFalse(EnchantedGoldCityLogic.isGoldMark(null));
    }

    @Test
    void 过滤已开与黑名单() {
        List<AncientChestStore.Mark> all = List.of(
            mark(0, 0, AncientChestStore.LABEL_GOLD),
            mark(1000, 0, AncientChestStore.LABEL_GOLD),
            mark(2000, 0, AncientChestStore.LABEL_SWIFT),
            mark(3000, 0, AncientChestStore.LABEL_BOTH));
        List<AncientChestStore.Mark> out = EnchantedGoldCityLogic.filterTargets(all,
            m -> m.x() != 1000);
        assertEquals(2, out.size());
        assertEquals(0, out.get(0).x());
        assertEquals(3000, out.get(1).x());
    }

    @Test
    void 两座城正确聚类() {
        List<AncientChestStore.Mark> marks = List.of(
            mark(0, 0, AncientChestStore.LABEL_GOLD),
            mark(20, 10, AncientChestStore.LABEL_BOTH),
            mark(500, 0, AncientChestStore.LABEL_GOLD),
            mark(520, 5, AncientChestStore.LABEL_GOLD));
        List<EnchantedGoldCityLogic.City> cities = EnchantedGoldCityLogic.groupCities(marks, 128);
        assertEquals(2, cities.size());
        // 大城优先（两座都是 2 个，按中心坐标稳定排序）
        int total = cities.stream().mapToInt(EnchantedGoldCityLogic.City::size).sum();
        assertEquals(4, total);
    }

    @Test
    void 聚类半径边界() {
        List<AncientChestStore.Mark> marks = List.of(
            mark(0, 0, AncientChestStore.LABEL_GOLD),
            mark(128, 0, AncientChestStore.LABEL_GOLD));
        assertEquals(1, EnchantedGoldCityLogic.groupCities(marks, 128).size());
        assertEquals(2, EnchantedGoldCityLogic.groupCities(marks, 127).size());
    }

    @Test
    void 按距离排序城市() {
        List<EnchantedGoldCityLogic.City> cities = EnchantedGoldCityLogic.groupCities(List.of(
            mark(0, 0, AncientChestStore.LABEL_GOLD),
            mark(1000, 0, AncientChestStore.LABEL_GOLD)), 64);
        List<EnchantedGoldCityLogic.City> sorted = EnchantedGoldCityLogic.sortedByDistance(cities, 990, 0);
        assertEquals(1000, sorted.get(0).centerX());
    }

    @Test
    void 去重按水平坐标() {
        List<AncientChestStore.Mark> out = EnchantedGoldCityLogic.distinct(List.of(
            mark(1, 2, AncientChestStore.LABEL_GOLD),
            new AncientChestStore.Mark(1, -40, 2, AncientChestStore.LABEL_BOTH)));
        assertEquals(1, out.size());
    }
}
