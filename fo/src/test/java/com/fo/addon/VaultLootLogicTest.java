package com.fo.addon;

import com.fo.addon.utils.VaultLootLogic;
import com.fo.addon.utils.VaultLootTarget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultLootLogicTest {
    private static VaultLootLogic.Prediction prediction(int offset, String... items) {
        List<VaultLootLogic.ItemCount> list = new ArrayList<>();
        for (String item : items) {
            int idx = item.lastIndexOf(':');
            list.add(new VaultLootLogic.ItemCount(item.substring(0, idx), Integer.parseInt(item.substring(idx + 1))));
        }
        return new VaultLootLogic.Prediction(offset, list);
    }

    @Test
    void occurrenceFiltersByTarget() {
        List<VaultLootLogic.Prediction> predictions = List.of(
            prediction(1, "minecraft:book:1", "minecraft:enchanted_golden_apple:1"),
            prediction(2, "minecraft:book:1"),
            prediction(3, "minecraft:heavy_core:1"));

        List<VaultLootLogic.Occurrence> apples = VaultLootLogic.occurrences(predictions, VaultLootTarget.ENCHANTED_GOLDEN_APPLE);
        assertEquals(1, apples.size());
        assertEquals(1, apples.get(0).offset());

        List<VaultLootLogic.Occurrence> cores = VaultLootLogic.occurrences(predictions, VaultLootTarget.HEAVY_CORE);
        assertEquals(1, cores.size());
        assertEquals(3, cores.get(0).offset());

        List<VaultLootLogic.Occurrence> both = VaultLootLogic.occurrences(predictions, VaultLootTarget.BOTH);
        assertEquals(2, both.size());
    }

    @Test
    void formatOccurrenceShowsGap() {
        VaultLootLogic.Occurrence occurrence = new VaultLootLogic.Occurrence(7, "minecraft:heavy_core", 1);
        assertEquals("第 7 次开箱 → 沉重核心（还差 4 次）", VaultLootLogic.formatOccurrence(3, occurrence));
    }

    @Test
    void firstNLimits() {
        List<VaultLootLogic.Occurrence> all = List.of(
            new VaultLootLogic.Occurrence(2, "minecraft:heavy_core", 1),
            new VaultLootLogic.Occurrence(5, "minecraft:heavy_core", 1),
            new VaultLootLogic.Occurrence(9, "minecraft:heavy_core", 1));
        assertEquals(2, VaultLootLogic.firstN(all, 2).size());
        assertEquals(3, VaultLootLogic.firstN(all, 20).size());
    }

    @Test
    void specJoinsItems() {
        String spec = VaultLootLogic.spec(List.of(
            new VaultLootLogic.ItemCount("minecraft:book", 1),
            new VaultLootLogic.ItemCount("minecraft:wind_charge", 12)));
        assertEquals("minecraft:book:1;minecraft:wind_charge:12", spec);
    }

    @Test
    void occurrencesFromHitsCovertsHits() {
        List<VaultLootLogic.Occurrence> occurrences = VaultLootLogic.occurrencesFromHits(List.of(
            new VaultLootLogic.Hit(4, "minecraft:enchanted_golden_apple", 1),
            new VaultLootLogic.Hit(11, "minecraft:heavy_core", 1)));
        assertEquals(2, occurrences.size());
        assertEquals(4, occurrences.get(0).offset());
        assertEquals("minecraft:heavy_core", occurrences.get(1).id());
    }

    @Test
    void windowGrowsWithElapsedTime() {
        assertEquals(30, VaultLootLogic.windowForSeconds(5));
        assertEquals(30, VaultLootLogic.windowForSeconds(10));
        assertEquals(60, VaultLootLogic.windowForSeconds(11));
        assertEquals(60, VaultLootLogic.windowForSeconds(20));
        assertEquals(120, VaultLootLogic.windowForSeconds(25));
        assertEquals(120, VaultLootLogic.windowForSeconds(40));
        assertEquals(200, VaultLootLogic.windowForSeconds(41));
        assertEquals(200, VaultLootLogic.windowForSeconds(60));
        assertEquals(400, VaultLootLogic.windowForSeconds(120));
        assertEquals(1000, VaultLootLogic.windowForSeconds(600));
        assertEquals(1000, VaultLootLogic.windowForSeconds(100000));
    }

    @Test
    void gapReportShowsOthersAndSpeed() {
        String report = VaultLootLogic.formatGapReport(100, 131, 60);
        assertTrue(report.contains("全服开了 31 次"));
        assertTrue(report.contains("别人 30 次"));
        assertTrue(report.contains("30.0 次/分钟"));
    }

    @Test
    void targetMatches() {
        assertTrue(VaultLootTarget.BOTH.matches("minecraft:enchanted_golden_apple"));
        assertTrue(VaultLootTarget.BOTH.matches("minecraft:heavy_core"));
        assertFalse(VaultLootTarget.BOTH.matches("minecraft:book"));
        assertTrue(VaultLootTarget.HEAVY_CORE.matches("minecraft:heavy_core"));
        assertFalse(VaultLootTarget.HEAVY_CORE.matches("minecraft:enchanted_golden_apple"));
    }
}
