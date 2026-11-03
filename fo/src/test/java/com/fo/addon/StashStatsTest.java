package com.fo.addon;

import com.fo.addon.utils.StashStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 本次搬运统计（V6.7）。 */
class StashStatsTest {

    @BeforeEach
    void resetStats() {
        StashStats.get().reset();
    }

    @Test
    void 初始为零() {
        assertEquals(0, StashStats.get().trips());
        assertEquals(0, StashStats.get().shulkers());
    }

    @Test
    void 累加趟数与潜影盒() {
        StashStats stats = StashStats.get();
        stats.addTrip();
        stats.addShulkers(3);
        stats.addTrip();
        stats.addShulkers(2);
        assertEquals(2, stats.trips());
        assertEquals(5, stats.shulkers());
    }

    @Test
    void 非正数不计数() {
        StashStats stats = StashStats.get();
        stats.addShulkers(0);
        stats.addShulkers(-5);
        assertEquals(0, stats.shulkers());
    }

    @Test
    void 重置清零() {
        StashStats stats = StashStats.get();
        stats.addTrip();
        stats.addShulkers(7);
        stats.reset();
        assertEquals(0, stats.trips());
        assertEquals(0, stats.shulkers());
    }

    @Test
    void 展示文案() {
        StashStats stats = StashStats.get();
        stats.addTrip();
        stats.addShulkers(9);
        assertEquals("1 趟 · 9 个潜影盒", stats.displayText());
    }
}
