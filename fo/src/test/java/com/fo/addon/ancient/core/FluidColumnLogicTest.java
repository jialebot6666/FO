package com.fo.addon.ancient.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 箱子上方整列：小水塘 vs 水下古城。 */
class FluidColumnLogicTest {

    @Test
    void 空列没有流体也不构成水下城() {
        assertFalse(FluidColumnLogic.hasFluid(new boolean[0]));
        assertFalse(FluidColumnLogic.hasFluid(null));
        assertFalse(FluidColumnLogic.fullySubmerged(new boolean[0]));
        assertFalse(FluidColumnLogic.isUnderwaterCity(new boolean[0]));
    }

    @Test
    void 半途有水只是水塘() {
        boolean[] column = {false, true, false};
        assertTrue(FluidColumnLogic.hasFluid(column));
        assertFalse(FluidColumnLogic.fullySubmerged(column));
        assertFalse(FluidColumnLogic.isUnderwaterCity(column));
        assertEquals(1, FluidColumnLogic.firstFluidIndex(column));
    }

    @Test
    void 整列水淹才是水下古城() {
        assertTrue(FluidColumnLogic.fullySubmerged(new boolean[]{true, true, true}));
        assertTrue(FluidColumnLogic.isUnderwaterCity(new boolean[]{true, true, true}));
        assertFalse(FluidColumnLogic.isUnderwaterCity(new boolean[]{true, true})); // 样本太少，保守不判
    }

    @Test
    void 没有流体时首索引为负() {
        assertEquals(-1, FluidColumnLogic.firstFluidIndex(new boolean[]{false, false}));
    }
}
