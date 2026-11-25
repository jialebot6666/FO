package com.fo.addon.ancient;

import com.fo.addon.ancient.core.PackGateLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 存盒门槛。 */
class PackGateLogicTest {

    @Test
    void thresholdIsInclusive() {
        assertTrue(PackGateLogic.shouldPack(64, 64));
        assertFalse(PackGateLogic.shouldPack(63, 64));
    }

    @Test
    void notTiedToTargetApples() {
        assertTrue(PackGateLogic.shouldPack(64, 64));
        assertTrue(PackGateLogic.shouldPack(70, 32));
    }

    @Test
    void thresholdAtLeastOne() {
        assertTrue(PackGateLogic.shouldPack(1, 0));
        assertFalse(PackGateLogic.shouldPack(0, 0));
    }
}
