package com.fo.addon;

import com.fo.addon.elytra.core.InventoryPickupLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「东西有没有真的进背包」的槽位快照判定（{@link InventoryPickupLogic}）。
 *
 * <p>V5.2 用它替代原来只看数量的做法。核心是<b>两个信号取或</b>：</p>
 * <ul>
 *   <li>总数变多 —— 覆盖「叠放进身上本来就有的那一摞」（槽位没变）；</li>
 *   <li>空槽位新出现目标物 —— 覆盖「总数没变但换了格子」。</li>
 * </ul>
 * <p>只用一个信号都会漏判，这里把两种情况都钉住。</p>
 */
class InventoryPickupLogicTest {

    private static int[] slots(int... counts) {
        return counts;
    }

    @Test
    void nothingChangesMeansNotPickedUp() {
        int[] a = slots(0, 1, 0, 0, 0, 0);
        assertFalse(InventoryPickupLogic.pickedUp(InventoryPickupLogic.of(a), InventoryPickupLogic.of(a.clone())));
    }

    @Test
    void stackedIntoExistingSlotIsDetectedByTotal() {
        // 身上第 1 格已有 1 个盒子，捡到的那 1 个叠了进去 -> 槽位分布没变，只有总数 +1
        int[] before = slots(0, 1, 0, 0);
        int[] after = slots(0, 2, 0, 0);
        assertTrue(InventoryPickupLogic.pickedUp(InventoryPickupLogic.of(before), InventoryPickupLogic.of(after)),
            "叠放进原来的摞里，必须靠总数判定");
        // 注意：这种情况槽位差是看不出来的，所以 newSlot 返回 -1
        assertEquals(-1, InventoryPickupLogic.newSlot(InventoryPickupLogic.of(before), InventoryPickupLogic.of(after)));
    }

    @Test
    void newSlotIsDetectedEvenWhenTotalUnchanged() {
        // 盒子从第 1 格换到了第 3 格：总数一样，但第 3 格原本是空的
        int[] before = slots(0, 1, 0, 0);
        int[] after = slots(0, 0, 0, 1);
        assertTrue(InventoryPickupLogic.pickedUp(InventoryPickupLogic.of(before), InventoryPickupLogic.of(after)),
            "总数没变但出现新槽位，也必须判为捡到");
        assertEquals(3, InventoryPickupLogic.newSlot(InventoryPickupLogic.of(before), InventoryPickupLogic.of(after)));
    }

    @Test
    void freshPickupIntoEmptyInventoryIsDetected() {
        int[] before = slots(0, 0, 0, 0);
        int[] after = slots(0, 0, 1, 0);
        assertTrue(InventoryPickupLogic.pickedUp(InventoryPickupLogic.of(before), InventoryPickupLogic.of(after)));
        assertEquals(2, InventoryPickupLogic.newSlot(InventoryPickupLogic.of(before), InventoryPickupLogic.of(after)));
    }

    @Test
    void losingAnItemIsNotAPickup() {
        // 总数变少（例如被火球炸掉、丢掉了）绝不能被当成捡到
        int[] before = slots(0, 2, 0, 0);
        int[] after = slots(0, 1, 0, 0);
        assertFalse(InventoryPickupLogic.pickedUp(InventoryPickupLogic.of(before), InventoryPickupLogic.of(after)));
    }

    @Test
    void sameTotalButDifferentSlotsIsAPickup() {
        // 两个槽位同时变化、总数不变：只要出现「原本为空、现在有」的格子就算捡到
        int[] before = slots(1, 0, 0, 0);
        int[] after = slots(0, 1, 0, 0);
        assertTrue(InventoryPickupLogic.pickedUp(InventoryPickupLogic.of(before), InventoryPickupLogic.of(after)));
    }

    @Test
    void snapshotIsImmutableCopy() {
        int[] live = slots(0, 1, 0);
        InventoryPickupLogic.Snapshot snap = InventoryPickupLogic.of(live);
        live[1] = 99;   // 拍完快照后原数组被改，快照不应该跟着变
        assertEquals(1, snap.total());
        assertEquals(1, snap.perSlot()[1]);
    }

    @Test
    void nullsAreHandledSafely() {
        InventoryPickupLogic.Snapshot empty = InventoryPickupLogic.of(null);
        assertEquals(0, empty.total());
        assertFalse(InventoryPickupLogic.pickedUp(null, empty));
        assertFalse(InventoryPickupLogic.pickedUp(empty, null));
        assertEquals(-1, InventoryPickupLogic.newSlot(null, null));
    }

    @Test
    void countsOccupiedSlots() {
        assertEquals(0, InventoryPickupLogic.occupiedSlots(InventoryPickupLogic.of(slots(0, 0, 0))));
        assertEquals(2, InventoryPickupLogic.occupiedSlots(InventoryPickupLogic.of(slots(0, 3, 0, 1))));
        assertEquals(0, InventoryPickupLogic.occupiedSlots(null));
    }

    @Test
    void arraysOfDifferentLengthDoNotThrow() {
        InventoryPickupLogic.Snapshot shortSnap = InventoryPickupLogic.of(slots(0, 1));
        InventoryPickupLogic.Snapshot longSnap = InventoryPickupLogic.of(slots(0, 0, 5, 0));
        // 不抛异常即可；两个信号都命中（总数 1 -> 5）
        assertTrue(InventoryPickupLogic.pickedUp(shortSnap, longSnap));
    }
}
