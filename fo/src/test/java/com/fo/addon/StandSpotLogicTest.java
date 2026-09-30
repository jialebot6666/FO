package com.fo.addon;

import com.fo.addon.utils.StandSpotLogic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 潜影盒交互站立点候选生成防回归测试（V4.16）。
 * 修复：存沙/补给/INIT_SCAN 寻路此前用 GoalXZ（ignoreY）只给盒子 xz，Y 轴被忽略——
 * 盒子在沙丘顶/坑里时 3D 到达判定永远不满足、开盒也够不到。
 * 新逻辑：先生成盒子旁带 Y 的站立点候选，模块过滤后选最近者作为寻路目标。
 */
public class StandSpotLogicTest {

    // 默认半径 2：水平 (5x5-1)=24 格 × 3 层 = 72 个候选，覆盖盒子周围全部可站方位
    @Test
    void radiusTwoCovers24HorizontalTimes3Vertical() {
        List<int[]> cs = StandSpotLogic.candidates(0, 64, 0, 2);
        assertEquals(24 * 3, cs.size());
    }

    // 候选覆盖水平 ±radius、Y ∈ [boxY-1, boxY+1]
    @Test
    void candidatesCoverExpectedRanges() {
        for (int[] c : StandSpotLogic.candidates(10, 100, -5, 2)) {
            assertTrue(Math.abs(c[0] - 10) <= 2, "x 越界: " + c[0]);
            assertTrue(Math.abs(c[2] - (-5)) <= 2, "z 越界: " + c[2]);
            assertTrue(c[1] >= 99 && c[1] <= 101, "y 越界: " + c[1]);
        }
    }

    // 盒子自身所在格（实体方块）必须被排除，不能作为站立点
    @Test
    void boxCellItselfIsExcluded() {
        for (int[] c : StandSpotLogic.candidates(3, 4, 5, 1)) {
            assertFalse(c[0] == 3 && c[1] == 4 && c[2] == 5, "盒子自身格未被排除");
        }
    }

    // 半径 0 或负数：按至少 1 处理（水平 8 格 × 3 层 = 24），保证总有候选兜底
    @Test
    void radiusAtLeastOne() {
        assertEquals(24, StandSpotLogic.candidates(5, 5, 5, 0).size());
        assertEquals(24, StandSpotLogic.candidates(5, 5, 5, -3).size());
    }

    // 候选与盒子同层（dy=0）的格子存在：玩家站在盒子旁边同一层是最理想交互位
    @Test
    void sameLayerCandidatesExist() {
        boolean found = false;
        for (int[] c : StandSpotLogic.candidates(0, 64, 0, 1)) {
            if (c[1] == 64 && c[0] != 0 && c[2] == 0 && Math.abs(c[0]) == 1) { found = true; break; }
        }
        assertTrue(found, "缺少与盒子同层的水平相邻候选");
    }

    // V4.17: withoutBelow 过滤掉盒子正下方（Y-1 层），保留同一层与上一层——站到盒子下面开盒打不开
    @Test
    void withoutBelowRemovesBelowLayer() {
        List<int[]> cs = StandSpotLogic.withoutBelow(64, StandSpotLogic.candidates(0, 64, 0, 2));
        assertEquals(24 * 2, cs.size(), "过滤后应只剩同一层+上一层 = 48 个候选");
        for (int[] c : cs) {
            assertTrue(c[1] >= 64, "出现盒子正下方候选: " + c[1]);
        }
    }

    // V4.17: withoutBelow 保留同层水平相邻候选（玩家站在盒子面前开盒的理想位置）
    @Test
    void withoutBelowKeepsSameLayerNeighbors() {
        boolean found = false;
        for (int[] c : StandSpotLogic.withoutBelow(64, StandSpotLogic.candidates(0, 64, 0, 1))) {
            if (c[1] == 64 && Math.abs(c[0]) == 1 && c[2] == 0) { found = true; break; }
        }
        assertTrue(found, "过滤后缺少同层相邻候选");
    }

    // V4.19: adjacentCandidates 生成盒子紧邻格（水平±1）= 同层 8 + 上一层 8 = 16，
    // 不含盒子自身格、不含盒子正下方（Y-1）
    @Test
    void adjacentCandidatesCover16Spots() {
        List<int[]> cs = StandSpotLogic.adjacentCandidates(0, 64, 0);
        assertEquals(16, cs.size());
        for (int[] c : cs) {
            assertTrue(Math.abs(c[0]) <= 1 && Math.abs(c[2]) <= 1, "越出紧邻范围: " + c[0] + "," + c[2]);
            assertTrue(c[1] == 64 || c[1] == 65, "Y 不在同层/上一层: " + c[1]);
            assertFalse(c[0] == 0 && c[1] == 64 && c[2] == 0, "盒子自身格未被排除");
        }
    }

    // V4.19: 紧邻格必须包含同层水平相邻格（玩家与盒子一格空隙都不留的交互位）
    @Test
    void adjacentCandidatesIncludeSameLayerNeighbors() {
        boolean found = false;
        for (int[] c : StandSpotLogic.adjacentCandidates(0, 64, 0)) {
            if (c[1] == 64 && Math.abs(c[0]) == 1 && c[2] == 0) { found = true; break; }
        }
        assertTrue(found, "紧邻候选缺少同层水平相邻格");
    }
}
