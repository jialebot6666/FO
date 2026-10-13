package com.fo.addon;

import com.fo.addon.utils.StoreSlotLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 存沙盒槽位同步判满防回归测试（V4.14）。
 * 借鉴 miku 的同步判满（读盒槽当场决定，不等 20tick 卡住检测），
 * 但收紧为"只认沙"：非沙槽位一律视为占用，杜绝 miku 把其他物品也存进去的 bug。
 */
public class StoreSlotLogicTest {

    private static final int MAX_STACK = 64;

    /** counts[i]=槽内数量，sand[i]=槽内是否沙；空槽 counts=0 */
    private static int[] counts(int... v) { return v; }

    private static boolean[] sand(boolean... v) { return v; }

    // 空盒 → 返回第一个空槽
    @Test
    void emptyBoxReturnsFirstSlot() {
        assertEquals(0, StoreSlotLogic.findSandTargetSlot(
            counts(0, 0, 0), sand(false, false, false), MAX_STACK, 64));
    }

    // 有半组沙且装得下整组 → 优先堆叠到该槽（省空间）
    @Test
    void partialSandSlotAbsorbsIncoming() {
        assertEquals(1, StoreSlotLogic.findSandTargetSlot(
            counts(0, 30, 0), sand(false, true, false), MAX_STACK, 34));
    }

    // 半组沙装不下整组 → 退而求其次找空槽（槽1是空槽）
    @Test
    void partialSandOverflowFallsBackToEmptySlot() {
        assertEquals(1, StoreSlotLogic.findSandTargetSlot(
            counts(40, 0), sand(true, false), MAX_STACK, 64));
    }

    // 全被非沙物品占满 → -1（盒满换盒），绝不往非沙槽存
    @Test
    void boxFullOfOtherItemsReturnsMinusOne() {
        assertEquals(-1, StoreSlotLogic.findSandTargetSlot(
            counts(64, 64), sand(false, false), MAX_STACK, 64));
    }

    // 沙槽全满 64 且无空槽 → -1
    @Test
    void boxFullOfSandReturnsMinusOne() {
        assertEquals(-1, StoreSlotLogic.findSandTargetSlot(
            counts(64, 64), sand(true, true), MAX_STACK, 64));
    }

    // 半组沙装不下、也没有空槽 → -1
    @Test
    void noRoomAtAllReturnsMinusOne() {
        assertEquals(-1, StoreSlotLogic.findSandTargetSlot(
            counts(40), sand(true), MAX_STACK, 64));
    }
}
