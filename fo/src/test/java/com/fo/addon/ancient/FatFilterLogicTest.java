package com.fo.addon.ancient;

import com.fo.addon.ancient.core.FatFilterLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 只挖肥箱过滤。 */
class FatFilterLogicTest {

    @Test
    void keepsEverythingWhenDisabled() {
        assertTrue(FatFilterLogic.keep(0, false, 3));
        assertTrue(FatFilterLogic.keep(1, false, 3));
    }

    @Test
    void thresholdIsInclusive() {
        assertTrue(FatFilterLogic.keep(3, true, 3));
        assertFalse(FatFilterLogic.keep(2, true, 3));
    }

    @Test
    void unknownCountIsDropped() {
        assertFalse(FatFilterLogic.keep(0, true, 3));
    }

    @Test
    void thresholdAtLeastOne() {
        assertTrue(FatFilterLogic.keep(1, true, 0));
        assertFalse(FatFilterLogic.keep(0, true, 0));
    }
}
