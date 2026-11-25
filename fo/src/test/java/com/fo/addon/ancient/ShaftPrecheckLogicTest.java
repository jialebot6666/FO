package com.fo.addon.ancient.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 井道 5 列预检的判定。 */
class ShaftPrecheckLogicTest {

    private static ShaftPrecheckLogic.ColumnScan clean() {
        return ShaftPrecheckLogic.ColumnScan.clean();
    }

    @Test
    void allCleanPasses() {
        var verdict = ShaftPrecheckLogic.evaluate(clean(), clean(), clean(), clean(), clean());
        assertTrue(verdict.pass());
        assertEquals(ShaftPrecheckLogic.Blocker.NONE, verdict.blocker());
    }

    @Test
    void fluidOnNorthBlocksWithPosition() {
        var verdict = ShaftPrecheckLogic.evaluate(clean(), clean(),
            new ShaftPrecheckLogic.ColumnScan(ShaftPrecheckLogic.Blocker.FLUID, -20),
            clean(), clean());
        assertFalse(verdict.pass());
        assertEquals(ShaftPrecheckLogic.Blocker.FLUID, verdict.blocker());
        assertTrue(verdict.reason().contains("南侧"));
        assertEquals(-20, verdict.blockerY());
    }

    @Test
    void amethystAloneDoesNotBlock() {
        var verdict = ShaftPrecheckLogic.evaluate(clean(), clean(), clean(), clean(),
            new ShaftPrecheckLogic.ColumnScan(ShaftPrecheckLogic.Blocker.AMETHYST, 30));
        assertTrue(verdict.pass(), "紫水晶能挖掉，不该拉黑");
    }

    @Test
    void shriekerBlocks() {
        var verdict = ShaftPrecheckLogic.evaluate(
            new ShaftPrecheckLogic.ColumnScan(ShaftPrecheckLogic.Blocker.SHRIEKER, 5),
            clean(), clean(), clean(), clean());
        assertFalse(verdict.pass());
        assertEquals(ShaftPrecheckLogic.Blocker.SHRIEKER, verdict.blocker());
    }

    @Test
    void fluidWinsOverUnbreakable() {
        var verdict = ShaftPrecheckLogic.evaluate(clean(),
            new ShaftPrecheckLogic.ColumnScan(ShaftPrecheckLogic.Blocker.UNBREAKABLE, 3),
            new ShaftPrecheckLogic.ColumnScan(ShaftPrecheckLogic.Blocker.FLUID, 2),
            clean(), clean());
        assertEquals(ShaftPrecheckLogic.Blocker.UNBREAKABLE, verdict.blocker(),
            "按列顺序，北侧先命中不可破");
    }

    @Test
    void worstPicksHighestSeverity() {
        var worst = ShaftPrecheckLogic.worst(List.of(
            ShaftPrecheckLogic.Blocker.AMETHYST,
            ShaftPrecheckLogic.Blocker.FALLING,
            ShaftPrecheckLogic.Blocker.FLUID,
            ShaftPrecheckLogic.Blocker.NONE));
        assertEquals(ShaftPrecheckLogic.Blocker.FLUID, worst);
    }

    @Test
    void worstOfEmptyIsNone() {
        assertEquals(ShaftPrecheckLogic.Blocker.NONE, ShaftPrecheckLogic.worst(List.of()));
        assertEquals(ShaftPrecheckLogic.Blocker.NONE, ShaftPrecheckLogic.worst(null));
    }
}
