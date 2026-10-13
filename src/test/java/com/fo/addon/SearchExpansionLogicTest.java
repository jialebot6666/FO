package com.fo.addon;

import com.fo.addon.utils.SearchExpansionLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 搜船范围扩展逻辑单测（对齐 Ying ElytraSearchFallback 环形外扩 / 50000 格上限） */
class SearchExpansionLogicTest {

    private static final int SPACING_BLOCKS = 176; // 末地城 spacing=11, 11*16=176

    // ===== ringCount =====

    @Test
    void ringCount_floorAt1() {
        assertEquals(1, SearchExpansionLogic.ringCount(0, SPACING_BLOCKS));
        assertEquals(1, SearchExpansionLogic.ringCount(100, SPACING_BLOCKS));
        assertEquals(1, SearchExpansionLogic.ringCount(175, SPACING_BLOCKS));
    }

    @Test
    void ringCount_scalesWithRange() {
        assertEquals(2, SearchExpansionLogic.ringCount(176, SPACING_BLOCKS));
        assertEquals(3, SearchExpansionLogic.ringCount(352, SPACING_BLOCKS));
        assertEquals(29, SearchExpansionLogic.ringCount(5000, SPACING_BLOCKS));
    }

    // ===== maxRing =====

    @Test
    void maxRing_hardLimit50000() {
        // 50000 格上限对应环数
        assertEquals(50000 / SPACING_BLOCKS + 1, SearchExpansionLogic.maxRing(SPACING_BLOCKS, 1));
    }

    @Test
    void maxRing_neverBelowBase() {
        // baseRing 低于 50000 格上限环数(285) 时，取上限环数
        assertEquals(285, SearchExpansionLogic.maxRing(SPACING_BLOCKS, 5));
        assertEquals(285, SearchExpansionLogic.maxRing(SPACING_BLOCKS, 285));
        // baseRing 超过 50000 格上限环数时，保留 baseRing（兜底）
        assertEquals(999, SearchExpansionLogic.maxRing(SPACING_BLOCKS, 999));
    }

    @Test
    void maxExpandedRangeMatchesYing() {
        assertEquals(50000, SearchExpansionLogic.MAX_EXPANDED_RANGE);
    }

    // ===== isRingBoundary =====

    @Test
    void ringBoundary_edgesOnly() {
        assertTrue(SearchExpansionLogic.isRingBoundary(1, 0, 1));
        assertTrue(SearchExpansionLogic.isRingBoundary(-1, 0, 1));
        assertTrue(SearchExpansionLogic.isRingBoundary(0, 1, 1));
        assertTrue(SearchExpansionLogic.isRingBoundary(0, -1, 1));
        assertTrue(SearchExpansionLogic.isRingBoundary(2, 5, 2));   // 水平边
        assertTrue(SearchExpansionLogic.isRingBoundary(-3, 2, 2));  // 垂直边 (|drz|==2)
    }

    @Test
    void ringBoundary_interiorNotIncluded() {
        // 环内部（|drx|<ring 且 |drz|<ring）不属于新环带，不重扫
        assertFalse(SearchExpansionLogic.isRingBoundary(0, 0, 2));
        assertFalse(SearchExpansionLogic.isRingBoundary(1, 1, 2));
        assertFalse(SearchExpansionLogic.isRingBoundary(-1, -1, 2));
    }

    @Test
    void ringBoundary_ring0Invalid() {
        assertFalse(SearchExpansionLogic.isRingBoundary(0, 0, 0));
        assertFalse(SearchExpansionLogic.isRingBoundary(1, 1, 0));
    }
}
