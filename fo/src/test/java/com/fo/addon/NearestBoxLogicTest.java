package com.fo.addon;

import com.fo.addon.utils.NearestBoxLogic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 最近存沙盒选择防回归测试（V4.14）。
 * 修复：旧 findOtherShulker 返回扫描顺序第一个盒（可能横跨 64 格），且会把
 * INIT_SCAN 跳过（名字未同步）的盒子误当存沙盒 → 沙存进补给盒。
 * 新逻辑：只在"名字已同步"的候选里选最近的，并支持排除集合（满盒/打不开的盒）。
 */
public class NearestBoxLogicTest {

    // 候选 [x,y,z] 列表，返回最近候选的下标；无可用候选返回 -1
    @Test
    void picksNearestCandidate() {
        List<int[]> boxes = List.of(
            new int[]{10, 64, 10},   // 远
            new int[]{2, 64, 1},     // 最近
            new int[]{-8, 64, 3}     // 中
        );
        assertEquals(1, NearestBoxLogic.select(0, 64, 0, boxes, Set.of()));
    }

    // 排除集合里的下标不可选（满盒/打不开/名字未同步的盒）
    @Test
    void excludedCandidatesAreSkipped() {
        List<int[]> boxes = List.of(
            new int[]{1, 64, 0},     // 最近但被排除（如已满）
            new int[]{5, 64, 5}      // 次近，应选它
        );
        assertEquals(1, NearestBoxLogic.select(0, 64, 0, boxes, Set.of(0)));
    }

    // 全部被排除 → -1（模块据此报"所有存沙盒已满"）
    @Test
    void allExcludedReturnsMinusOne() {
        List<int[]> boxes = List.of(new int[]{1, 64, 0}, new int[]{2, 64, 0});
        assertEquals(-1, NearestBoxLogic.select(0, 64, 0, boxes, Set.of(0, 1)));
    }

    // 空候选 → -1
    @Test
    void emptyCandidatesReturnMinusOne() {
        assertEquals(-1, NearestBoxLogic.select(0, 64, 0, List.of(), Set.of()));
    }

    // 距离用三维平方距离（盒子在崖上/坑里也要算 Y）
    @Test
    void verticalDistanceCounts() {
        List<int[]> boxes = List.of(
            new int[]{0, 90, 0},     // 头顶 26 格
            new int[]{3, 64, 0}      // 水平 3 格 → 更近
        );
        assertEquals(1, NearestBoxLogic.select(0, 64, 0, boxes, Set.of()));
    }
}
