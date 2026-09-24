package com.fo.addon;

import com.fo.addon.utils.SandPickupLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Nuker 拾取决策防回归测试。
 */
public class SandPickupLogicTest {

    @Test
    void notPickingUpAndNoDrops_mine() {
        assertEquals(SandPickupLogic.Action.MINE, SandPickupLogic.decide(false, false));
    }

    @Test
    void notPickingUpAndHasDrops_startPickup() {
        assertEquals(SandPickupLogic.Action.START, SandPickupLogic.decide(true, false));
    }

    @Test
    void pickingUpAndStillHasDrops_keepPicking() {
        assertEquals(SandPickupLogic.Action.KEEP, SandPickupLogic.decide(true, true));
    }

    @Test
    void pickingUpAndNoMoreDrops_stopAndMine() {
        assertEquals(SandPickupLogic.Action.STOP_AND_MINE, SandPickupLogic.decide(false, true));
    }
}
