package com.fo.addon.ancient.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 竖井流体判定：下一格、四邻、前瞻、不可破坏。 */
class ShaftFluidLogicTest {

    @Test
    void 下一格有水立即停() {
        assertEquals(ShaftFluidLogic.Risk.FLUID,
            ShaftFluidLogic.assess(true, false, false, false, false, new boolean[]{false, false}, false));
    }

    @Test
    void 四邻任一方向有流体都停() {
        for (int dir = 0; dir < 4; dir++) {
            boolean[] n = new boolean[4];
            n[dir] = true;
            assertEquals(ShaftFluidLogic.Risk.FLUID,
                ShaftFluidLogic.assess(false, n[0], n[1], n[2], n[3], new boolean[]{false}, false),
                "方向 " + dir + " 应触发");
        }
    }

    @Test
    void 前瞻有流体提前停() {
        assertEquals(ShaftFluidLogic.Risk.LOOKAHEAD_FLUID,
            ShaftFluidLogic.assess(false, false, false, false, false, new boolean[]{false, true}, false));
    }

    @Test
    void 不可破坏方块停() {
        assertEquals(ShaftFluidLogic.Risk.UNBREAKABLE,
            ShaftFluidLogic.assess(false, false, false, false, false, new boolean[]{false}, true));
    }

    @Test
    void 流体优先于不可破坏() {
        assertEquals(ShaftFluidLogic.Risk.FLUID,
            ShaftFluidLogic.assess(true, false, false, false, false, new boolean[]{false}, true));
    }

    @Test
    void 全部安全才继续() {
        assertEquals(ShaftFluidLogic.Risk.SAFE,
            ShaftFluidLogic.assess(false, false, false, false, false, new boolean[]{false, false}, false));
        assertFalse(ShaftFluidLogic.mustStop(ShaftFluidLogic.Risk.SAFE));
        assertTrue(ShaftFluidLogic.mustStop(ShaftFluidLogic.Risk.LOOKAHEAD_FLUID));
    }
}
