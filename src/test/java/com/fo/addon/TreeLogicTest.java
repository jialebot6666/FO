package com.fo.addon;

import com.fo.addon.utils.TreeLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TreeLogicTest {

    @Test
    void bonemealWhenSaplingPresent() {
        assertEquals(TreeLogic.Action.BONEMEAL,
            TreeLogic.decide(true, false, true, true, true));
    }

    @Test
    void noBonemealWhenDisabled() {
        assertEquals(TreeLogic.Action.NONE,
            TreeLogic.decide(true, false, false, true, true));
    }

    @Test
    void noBonemealWhenNoBoneMeal() {
        assertEquals(TreeLogic.Action.NONE,
            TreeLogic.decide(true, false, true, false, true));
    }

    @Test
    void plantWhenAirAndHasSapling() {
        assertEquals(TreeLogic.Action.PLANT,
            TreeLogic.decide(false, true, true, true, true));
    }

    @Test
    void noneWhenBlocked() {
        // 上方既不是树苗也不是空气（有方块挡住）
        assertEquals(TreeLogic.Action.NONE,
            TreeLogic.decide(false, false, true, true, true));
    }

    @Test
    void noneWhenNoSaplingToPlant() {
        assertEquals(TreeLogic.Action.NONE,
            TreeLogic.decide(false, true, true, true, false));
    }

    @Test
    void bonemealTakesPriorityOverPlant() {
        // 树苗已种下但状态为可替换（理论边缘情况），骨粉优先
        assertEquals(TreeLogic.Action.BONEMEAL,
            TreeLogic.decide(true, true, true, true, true));
    }

    @Test
    void noPlantingWhenBonemealEnabledButMissing() {
        // 开骨粉但无骨粉：空气位也不种树，整体暂停等待骨粉补充
        // (防止只种不催熟导致树苗耗尽)
        assertEquals(TreeLogic.Action.NONE,
            TreeLogic.decide(false, true, true, false, true));
    }

    @Test
    void noPlantingWhenBonemealEnabledButMissingAndSaplingUp() {
        // 开骨粉但无骨粉：已种树苗也不催熟（无骨粉），且不种新苗
        assertEquals(TreeLogic.Action.NONE,
            TreeLogic.decide(true, false, true, false, true));
    }

    @Test
    void plantingStillWorksWhenBonemealDisabled() {
        // 关闭骨粉：照常种树苗
        assertEquals(TreeLogic.Action.PLANT,
            TreeLogic.decide(false, true, false, false, true));
    }
}
