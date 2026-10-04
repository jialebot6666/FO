package com.fo.addon;

import com.fo.addon.utils.PickupNextLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 拾取完成后下一步决策单测（V4.49 对齐 misaka x0275 case 0/1/2） */
class PickupNextLogicTest {

    // ===== 拾取潜影盒（case 0） =====

    @Test
    void shulkerFullBoxImmediatelyPlacesEnderChest() {
        // 拾取完潜影盒、背包有满盒 → 立即放末影箱换空盒（工作台延后），不再等下次合成
        assertEquals(PickupNextLogic.PickupNext.PLACE_ENDER_CHEST,
            PickupNextLogic.decide(PickupNextLogic.PickupKind.SHULKER_BOX, true, true));
        assertEquals(PickupNextLogic.PickupNext.PLACE_ENDER_CHEST,
            PickupNextLogic.decide(PickupNextLogic.PickupKind.SHULKER_BOX, true, false));
    }

    @Test
    void shulkerNoFullBoxMinesTableFirst() {
        // 无满盒 + 有工作台待挖 → 先挖工作台
        assertEquals(PickupNextLogic.PickupNext.MINE_CRAFTING_TABLE,
            PickupNextLogic.decide(PickupNextLogic.PickupKind.SHULKER_BOX, false, true));
    }

    @Test
    void shulkerNoFullBoxNoTableGoesMining() {
        // 无满盒 + 无工作台 → 直接回挖矿
        assertEquals(PickupNextLogic.PickupNext.MINING,
            PickupNextLogic.decide(PickupNextLogic.PickupKind.SHULKER_BOX, false, false));
    }

    // ===== 拾取末影箱（case 2）：换盒链完成 =====

    @Test
    void enderChestThenMinesTable() {
        // 换完空盒 → 有工作台待挖 → 挖工作台（misaka：末影箱拾取后 x0180 非空 → MINING_CRAFTING_TABLE）
        assertEquals(PickupNextLogic.PickupNext.MINE_CRAFTING_TABLE,
            PickupNextLogic.decide(PickupNextLogic.PickupKind.ENDER_CHEST, true, true));
        assertEquals(PickupNextLogic.PickupNext.MINE_CRAFTING_TABLE,
            PickupNextLogic.decide(PickupNextLogic.PickupKind.ENDER_CHEST, false, true));
    }

    @Test
    void enderChestNoTableGoesMining() {
        // 换完空盒、无工作台 → 回挖矿
        assertEquals(PickupNextLogic.PickupNext.MINING,
            PickupNextLogic.decide(PickupNextLogic.PickupKind.ENDER_CHEST, true, false));
    }

    // ===== 拾取工作台（case 1） =====

    @Test
    void craftingTableAlwaysGoesMining() {
        // 工作台拾取完 → 直接回挖矿（misaka case 1，无论满盒与否）
        assertEquals(PickupNextLogic.PickupNext.MINING,
            PickupNextLogic.decide(PickupNextLogic.PickupKind.CRAFTING_TABLE, true, true));
        assertEquals(PickupNextLogic.PickupNext.MINING,
            PickupNextLogic.decide(PickupNextLogic.PickupKind.CRAFTING_TABLE, false, false));
    }
}
