package com.fo.addon;

import com.fo.addon.utils.DirectionFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 搜索方向过滤逻辑测试 (对应 ElytraDirectionFilterV2 移植) */
public class DirectionFilterTest {

    @Test
    void eastShipKeptWhenEastEnabled() {
        // 玩家在 (0,0)，船在 (100, 5)：|dx|>|dz| → 东向
        assertTrue(DirectionFilter.shouldKeep(0, 0, 100, 5, true, true, true, true));
        assertTrue(DirectionFilter.shouldKeep(0, 0, 100, 5, false, false, true, false));
        assertFalse(DirectionFilter.shouldKeep(0, 0, 100, 5, true, true, false, true));
    }

    @Test
    void westShipKeptWhenWestEnabled() {
        // 船在 (-100, 5)：西向
        assertTrue(DirectionFilter.shouldKeep(0, 0, -100, 5, true, true, true, true));
        assertTrue(DirectionFilter.shouldKeep(0, 0, -100, 5, false, false, false, true));
        assertFalse(DirectionFilter.shouldKeep(0, 0, -100, 5, true, true, true, false));
    }

    @Test
    void southShipKeptWhenSouthEnabled() {
        // 船在 (5, 100)：|dz|>|dx| → 南向
        assertTrue(DirectionFilter.shouldKeep(0, 0, 5, 100, true, true, true, true));
        assertTrue(DirectionFilter.shouldKeep(0, 0, 5, 100, false, true, false, false));
        assertFalse(DirectionFilter.shouldKeep(0, 0, 5, 100, true, false, true, true));
    }

    @Test
    void northShipKeptWhenNorthEnabled() {
        // 船在 (5, -100)：北向
        assertTrue(DirectionFilter.shouldKeep(0, 0, 5, -100, true, true, true, true));
        assertTrue(DirectionFilter.shouldKeep(0, 0, 5, -100, true, false, false, false));
        assertFalse(DirectionFilter.shouldKeep(0, 0, 5, -100, false, true, true, true));
    }

    @Test
    void allDisabledDropsEverything() {
        assertFalse(DirectionFilter.shouldKeep(0, 0, 100, 0, false, false, false, false));
        assertFalse(DirectionFilter.shouldKeep(0, 0, 0, 100, false, false, false, false));
    }

    @Test
    void diagonalPrefersAxisWithLargerDelta() {
        // 船在 (100, 80)：|dx|=100 >= |dz|=80 → 东向判定
        assertTrue(DirectionFilter.shouldKeep(0, 0, 100, 80, false, false, true, false));
        assertFalse(DirectionFilter.shouldKeep(0, 0, 100, 80, false, false, false, true));
        // 船在 (80, 100)：|dz|=100 > |dx|=80 → 南向判定
        assertTrue(DirectionFilter.shouldKeep(0, 0, 80, 100, false, true, false, false));
        assertFalse(DirectionFilter.shouldKeep(0, 0, 80, 100, true, false, false, false));
    }

    @Test
    void exactDiagonalGoesEast() {
        // |dx| == |dz|：东西向优先 (dx>=0 → 东)
        assertTrue(DirectionFilter.shouldKeep(0, 0, 100, 100, false, false, true, false));
        assertFalse(DirectionFilter.shouldKeep(0, 0, 100, 100, false, true, false, false));
    }
}
