package com.fo.addon;

import com.fo.addon.elytra.core.FreeSlotLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 背包满时腾位的选格与槽位号换算（{@link FreeSlotLogic}）。
 *
 * <p>这是「丢东西」这条路径上最容易出人命的地方：{@code clickSlot} 用的是
 * <b>PlayerScreenHandler 的原始槽位号</b>，算错一格就会丢掉旁边的东西。
 * 所以把映射和选格规则都钉死。</p>
 */
class FreeSlotLogicTest {

    // ------------------------------------------------------------------ 槽位号映射

    @Test
    void hotbarMapsToRaw36To44() {
        for (int i = 0; i < 9; i++) {
            assertEquals(36 + i, FreeSlotLogic.rawPlayerSlotId(i), "快捷栏下标 " + i);
        }
    }

    @Test
    void backpackMapsToItself() {
        for (int i = 9; i < 36; i++) {
            assertEquals(i, FreeSlotLogic.rawPlayerSlotId(i), "背包下标 " + i);
        }
    }

    @Test
    void outOfRangeReturnsMinusOne() {
        assertEquals(-1, FreeSlotLogic.rawPlayerSlotId(-1));
        assertEquals(-1, FreeSlotLogic.rawPlayerSlotId(36));
        assertEquals(-1, FreeSlotLogic.rawPlayerSlotId(999));
    }

    // ------------------------------------------------------------------ 空位判断

    private static boolean[] slots(boolean... flags) {
        return flags;
    }

    @Test
    void hasEmptySlotDetectsAnyEmpty() {
        assertTrue(FreeSlotLogic.hasEmptySlot(slots(true, false, false)));
        assertFalse(FreeSlotLogic.hasEmptySlot(slots(false, false, false)));
        assertFalse(FreeSlotLogic.hasEmptySlot(null));
    }

    // ------------------------------------------------------------------ 选格

    /** 造一个长度 36 的数组，把指定下标置为 true。 */
    private static boolean[] marks(int... indexes) {
        boolean[] a = new boolean[FreeSlotLogic.INVENTORY_SIZE];
        for (int i : indexes) a[i] = true;
        return a;
    }

    @Test
    void prefersBackpackOverHotbar() {
        // 背包第 20 格和快捷栏第 3 格都可丢 -> 应该挑背包（不动快捷栏排布）
        boolean[] empty = marks();
        boolean[] prot = new boolean[FreeSlotLogic.INVENTORY_SIZE];
        for (int i = 0; i < FreeSlotLogic.INVENTORY_SIZE; i++) prot[i] = true;
        prot[20] = false;   // 背包第 20 格可丢
        prot[3] = false;    // 快捷栏第 3 格也可丢
        assertEquals(20, FreeSlotLogic.pickDroppableSlot(empty, prot, -1),
            "背包和快捷栏都可丢时，必须优先动背包");
    }

    @Test
    void picksFirstBackpackSlotWhenAllDroppable() {
        boolean[] empty = marks();
        boolean[] prot = marks();
        assertEquals(9, FreeSlotLogic.pickDroppableSlot(empty, prot, -1), "应挑背包第一格(9)");
    }

    @Test
    void fallsBackToHotbarOnlyWhenBackpackHasNothingDroppable() {
        boolean[] empty = marks();
        boolean[] prot = marks();
        // 背包 9..35 全部受保护，快捷栏 0..8 除手持外都可丢
        for (int i = 9; i < 36; i++) prot[i] = true;
        assertEquals(0, FreeSlotLogic.pickDroppableSlot(empty, prot, 2), "背包没得丢才退到快捷栏");
    }

    @Test
    void neverPicksHeldHotbarSlot() {
        boolean[] empty = marks();
        boolean[] prot = marks();
        for (int i = 9; i < 36; i++) prot[i] = true;   // 背包全保护，只能动快捷栏
        prot[0] = false;
        prot[1] = false;
        assertEquals(1, FreeSlotLogic.pickDroppableSlot(empty, prot, 0),
            "手持第 0 格时不能丢第 0 格，应挑第 1 格");
    }

    @Test
    void skipsEmptySlots() {
        boolean[] empty = marks();
        boolean[] prot = marks();
        empty[9] = true;    // 第 9 格是空的 -> 不能「丢」空格
        empty[10] = true;
        assertEquals(11, FreeSlotLogic.pickDroppableSlot(empty, prot, -1));
    }

    @Test
    void returnsMinusOneWhenEverythingIsProtected() {
        boolean[] empty = marks();
        boolean[] prot = new boolean[FreeSlotLogic.INVENTORY_SIZE];
        for (int i = 0; i < FreeSlotLogic.INVENTORY_SIZE; i++) prot[i] = true;
        assertEquals(-1, FreeSlotLogic.pickDroppableSlot(empty, prot, -1),
            "全是保护物品时应该挑不出来（调用方会报中文提示而不是乱丢）");
    }

    @Test
    void returnsMinusOneOnNullInputs() {
        assertEquals(-1, FreeSlotLogic.pickDroppableSlot(null, marks(), -1));
        assertEquals(-1, FreeSlotLogic.pickDroppableSlot(marks(), null, -1));
    }

    @Test
    void shortArraysDoNotThrow() {
        boolean[] empty = new boolean[3];
        boolean[] prot = new boolean[3];
        // 不抛异常即可（真实调用永远是 36 格，这里只是防御）
        assertTrue(FreeSlotLogic.pickDroppableSlot(empty, prot, -1) >= -1);
    }
}
