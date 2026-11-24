package com.fo.addon;

import com.fo.addon.utils.VaultEspLogic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 不祥宝库 ESP 判定逻辑测试（6.9p24 新增）。
 *
 * <p>锁死三条行为：
 * ① 只有「宝库方块 + ominous=true」才算不祥宝库（普通宝库绝不能命中）；
 * ② 「只显示未开」开关的过滤语义；
 * ③ 渲染阶段的半径过滤 / 距离排序 / 数量上限。
 */
public class VaultEspLogicTest {

    @Test
    void matchesOnlyOminousVault() {
        assertTrue(VaultEspLogic.matches(true, true));
        assertFalse(VaultEspLogic.matches(true, false), "普通宝库（ominous=false）不应命中");
        assertFalse(VaultEspLogic.matches(false, true), "非宝库方块不应命中");
        assertFalse(VaultEspLogic.matches(false, false));
    }

    @Test
    void onlyUnopenedSwitch() {
        assertTrue(VaultEspLogic.shouldHighlight(true, true));
        assertFalse(VaultEspLogic.shouldHighlight(false, true), "只显示未开时，已开过的要藏起来");
        assertTrue(VaultEspLogic.shouldHighlight(false, false), "关掉开关后已开过的也要画");
        assertTrue(VaultEspLogic.shouldHighlight(true, false));
    }

    @Test
    void nearestFiltersByRadius() {
        List<VaultEspLogic.Hit> hits = List.of(
            new VaultEspLogic.Hit(0, 64, 10, true),
            new VaultEspLogic.Hit(0, 64, 600, true) // 半径 500 之外
        );
        List<VaultEspLogic.Hit> near = VaultEspLogic.nearest(hits, 0, 64, 0, 500, 32);
        assertEquals(1, near.size());
        assertEquals(10, near.get(0).z());
    }

    @Test
    void nearestSortsByDistanceAndLimits() {
        List<VaultEspLogic.Hit> hits = List.of(
            new VaultEspLogic.Hit(30, 64, 0, true),
            new VaultEspLogic.Hit(0, 64, 5, true),
            new VaultEspLogic.Hit(0, 64, 2, true)
        );
        List<VaultEspLogic.Hit> near = VaultEspLogic.nearest(hits, 0, 64, 0, 500, 2);
        assertEquals(2, near.size());
        assertEquals(2, near.get(0).z(), "最近的是 z=2");
        assertEquals(5, near.get(1).z(), "第二近的是 z=5");
    }

    @Test
    void nearestHandlesEmptyAndBadLimit() {
        assertTrue(VaultEspLogic.nearest(List.of(), 0, 64, 0, 500, 32).isEmpty());
        assertTrue(VaultEspLogic.nearest(
            List.of(new VaultEspLogic.Hit(0, 64, 1, true)), 0, 64, 0, 500, 0).isEmpty());
        assertTrue(VaultEspLogic.nearest(
            List.of(new VaultEspLogic.Hit(0, 64, 1, true)), 0, 64, 0, 0, 32).isEmpty());
    }

    @Test
    void defaultsMatchUserRequirement() {
        assertEquals(500, VaultEspLogic.DEFAULT_RADIUS, "扫描半径默认 500 格（对齐服务器可渲染范围）");
        assertEquals(32, VaultEspLogic.DEFAULT_LIMIT);
    }

    @Test
    void budgetSpent() {
        assertTrue(VaultEspLogic.budgetSpent(2_000_000L, 2_000_000L));
        assertTrue(VaultEspLogic.budgetSpent(3L, 2L));
        assertFalse(VaultEspLogic.budgetSpent(1L, 2L));
    }
}
